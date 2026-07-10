# Scalable Central Document Management System (DMS)

Course project for a cloud-native document archive aimed at public authorities. Authorities
share one platform to store, organize, search, and retrieve digital documents (scans, PDFs,
XML, JSON, …). Access is enforced with role-based permissions, documents are found through
full-text search (including OCR text from scans), and every file gets a legal file number
following the Bavarian *Einheitsaktenplan* (EAP).

**Stack:** Keycloak (auth), a Spring Boot REST + CMIS API, a React/Vite frontend, MongoDB for
metadata and file storage, and a standalone Tesseract OCR worker. It runs locally on **Docker
Compose** and in production on **Kubernetes**, where **Gitea** hosts the repository, runs the
CI/CD pipeline (Gitea Actions), and serves the OCI image registry. All self-built images are
based on **Debian 13 (trixie)**.

## Environments

| Environment | Platform | Config | Guide |
| --- | --- | --- | --- |
| **Development** | Docker Compose | `infra/docker-compose/` | this file, below |
| **Production** | Kubernetes (Gitea, CI/CD, OCI registry) | `infra/k8s/`, `infra/gitea/`, `.gitea/workflows/` | [`infra/k8s/README.md`](infra/k8s/README.md) |

Dev and prod deliberately use different configuration. Read
[`docs/production-configuration.md`](docs/production-configuration.md) before deploying outside a
developer machine.

## Installation — development (Docker Compose)

**Prerequisites:** Docker with Docker Compose v2 (check with `docker compose version`), and these
host ports free: **5173** (frontend), **8080** and **9000** (Keycloak), **8081** (backend), and
**27018** (MongoDB).

The stack has **no fallback passwords**: it refuses to start until every required secret is set.
The quickest first-time setup writes a `.env` with strong random secrets and prints the demo
login (the Compose counterpart to the Kubernetes `generate-secrets.sh`):

```bash
cd infra/docker-compose
bash generate-env.sh
```

Prefer to set them by hand? Copy the template and fill the five blank secrets yourself (any strong
value works, e.g. `openssl rand -hex 24`):

```bash
cp .env.example .env
```

The five required keys are:

- `KEYCLOAK_ADMIN_PASSWORD`
- `MONGO_ROOT_PASSWORD`
- `MONGO_APP_PASSWORD`
- `DMS_KEYCLOAK_ADMIN_CLIENT_SECRET` — any value; the same one is injected into both the Keycloak
  realm import and the backend, so nothing has to be matched by hand.
- `DMS_DEMO_USER_PASSWORD` — this becomes the login password for the demo users.

Then bring the stack up:

```bash
docker compose down -v      # wipe old volumes so realm import + Mongo init run cleanly
docker compose up --build
```

You do **not** configure Keycloak by hand — on startup Compose imports the `dms` realm from
`infra/keycloak/dms-realm.json`, and `scripts/mongo-init.js` creates the collections, indexes,
and the root folder `/` on first boot (empty volume).

Once the containers are healthy:

| Service | URL / port |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend (REST + CMIS) | http://localhost:8081 |
| Keycloak | http://localhost:8080 |
| MongoDB (host) | `127.0.0.1:27018` → 27017 in-container (override with `MONGO_HOST_PORT`) |

The frontend reads all URLs from `VITE_*` variables — Compose sets them for you; for a standalone
`npm run dev`, copy `frontend/.env.example` to `frontend/.env.local`.

## Installation — production (Kubernetes)

The full stack also runs on a single-node **minikube** cluster (MongoDB, Keycloak, backend,
frontend, OCR worker), with health checks, persistent storage, and replicas for the stateless
components. From the repo root on the cluster host:

```bash
bash infra/k8s/generate-secrets.sh   # once per cluster: writes 01-secrets.yaml (git-ignored)
bash infra/k8s/build-and-load.sh     # build the Debian 13 images and load them into minikube
bash infra/k8s/deploy.sh             # apply manifests and wait for pods to be Ready
```

Full deployment, access (SSH tunnels to the NodePorts), failover demos, and troubleshooting are
in [`infra/k8s/README.md`](infra/k8s/README.md). An optional 3-member MongoDB replica set with
automatic failover is available via `infra/k8s/02-mongodb-replicaset.yaml` +
`infra/k8s/init-mongodb-rs.sh`.

## Infrastructure & Gitea

Production infrastructure runs on Kubernetes and includes a self-hosted **Gitea**, which serves
three roles:

1. **Git hosting** — Gitea (deployed by `infra/k8s/07-gitea.yaml`, image in `infra/gitea/`) hosts
   the project repository inside the cluster.
2. **CI/CD** — [`.gitea/workflows/ci.yml`](.gitea/workflows/ci.yml) runs on the in-cluster Gitea
   Actions runner on every push: it **tests every component**, **builds the three Debian 13
   images**, **pushes them to the Gitea OCI registry**, and **rolls the Kubernetes Deployments**
   to the new images.
3. **OCI registry** — Gitea's registry (NodePort `30300`) both receives the pushed images and is
   where the kubelet pulls from during rollout.

See [`infra/gitea/README.md`](infra/gitea/README.md) for the Gitea server and Actions runner
images.

## Testing

Automated tests live next to the code and run with standard frameworks. They also run
automatically in the CI pipeline (the *test every component* stage) before any image is built.

| Component | Framework | Location | Run locally |
| --- | --- | --- | --- |
| Backend | JUnit 5 + Spring Boot Test + Testcontainers | `backend/src/test/java/` | `cd backend && ./mvnw test` |
| OCR worker | pytest | `ocr-worker/tests/` | `cd ocr-worker && pip install -r requirements-dev.txt && pytest` |
| Frontend | Vitest + Testing Library | `frontend/src/**/*.test.jsx` | `cd frontend && npm install && npm test` |

Backend coverage includes pure unit tests (RBAC resolver, upload validation, JWT role mapping)
and integration tests that run against a real MongoDB (document upload/download, 403 enforcement,
permission-safe search, CMIS). The integration tests connect to a MongoDB on `localhost:27017`
and self-skip when none is reachable; the CI pipeline starts and seeds one for them.

## Main workflow (frontend)

After signing in through Keycloak, the app exposes the core document workflow:

- `/` — Dashboard: the root folder's subfolders and documents.
- `/folders/:id` — Folder view with breadcrumb navigation; lists only what the backend permits.
- `/documents/:id` — Document detail: metadata, OCR status (auto-refreshes while pending or
  processing), and download. A 403 shows a clear access-denied message instead of content.
- `/upload` — Upload form (file + title, type, parent folder, department); `POST /api/documents`.
- `/search` — Full-text search over titles, EAP numbers, and OCR text.
- `/admin` — Admin-only live view of Keycloak users/roles/departments, permission scopes, service
  health, and repository counts.

Every API call attaches the Keycloak Bearer token. The UI never hides content with CSS — it
renders exactly what the backend returns and relies on 401/403 responses.

## Demo accounts (development only)

All four demo users share the value of `DMS_DEMO_USER_PASSWORD` from your `.env`. If you ran
`generate-env.sh` it is random — read it from the script's output (the `Demo login (all demo
users): …` line) or from `infra/docker-compose/.env` at any time. If you filled `.env` by hand,
it is whatever you set. Use it only locally.

- **admin@dms.local** — role `dms_admin`, no department.
- **manager@dms.local** — role `dms_department_manager`, department `ITDLZ`.
- **contributor@dms.local** — role `dms_contributor`, department `ITDLZ`.
- **viewer@dms.local** — role `dms_viewer`, department `ITDLZ`.

Sign in through the frontend (you are redirected to Keycloak) or through Keycloak on port 8080.

## Check that authentication works

Log in as `viewer@dms.local`, copy the `access_token` from the Keycloak token response in your
browser's Network tab, and call the backend:

```http
GET http://localhost:8081/api/auth/me
Authorization: Bearer <access_token>
```

A successful response includes your username and `realm_access.roles` with `dms_viewer`. Manager,
contributor, and viewer tokens also include `"department": "ITDLZ"` when the realm mapper is active.

Public health check (no token): `GET http://localhost:8081/health` → `{"status":"ok"}`.

## API documentation

- Interactive Swagger UI on the running backend: http://localhost:8081/swagger-ui.html
- OpenAPI spec: http://localhost:8081/v3/api-docs (static copy in [`docs/api.html`](docs/api.html))
- Generated code docs (Javadoc / JSDoc / pdoc) and regeneration commands: [`docs/README.md`](docs/README.md)

## Repository layout

- `backend/` — Spring Boot REST + CMIS API and tests (see `backend/README.md`).
- `frontend/` — React + Vite UI.
- `ocr-worker/` — Tesseract OCR worker that makes scans searchable (see `ocr-worker/README.md`).
- `infra/docker-compose/` — local development stack.
- `infra/k8s/` — Kubernetes manifests and deploy scripts (see `infra/k8s/README.md`).
- `infra/gitea/` — Gitea server + Actions runner images (see `infra/gitea/README.md`).
- `infra/keycloak/` — realm export for automatic import.
- `.gitea/workflows/` — CI/CD pipeline.
- `scripts/` — database initialization helpers.
- `docs/` — API and generated code documentation.

To verify MongoDB after a fresh start:
`docker compose exec mongodb mongosh dms --eval "db.folders.getIndexes()"`, then
`db.folders.findOne({ path: '/' })`.
