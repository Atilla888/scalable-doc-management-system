# Debian 13 test environment — first-time setup guide

This guide takes a **fresh Debian 13 (trixie) machine** from zero to a fully running,
verified DMS: first the Docker Compose development stack, then the Kubernetes
production-style deployment on minikube, and finally the automated test suites. No
Java, Node.js, or Python toolchain is installed on the host — everything runs in
containers, and every self-built image is based on Debian 13, matching the course
requirement for the container base.

It assumes a plain VM (or physical machine) with:

| Resource | Compose stack only | Compose + Kubernetes + Gitea CI |
| --- | --- | --- |
| vCPUs | 2 | 4 |
| RAM | 4 GB | 8 GB |
| Disk | 20 GB free | 40 GB free |
| Access | a sudo-capable user, internet access | same |

All commands are run as your normal user; only the package installation uses `sudo`.

## 1. Base system

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y git curl ca-certificates openssl python3
```

`python3` and `openssl` ship with Debian 13 by default; they are listed because the
secret generators (`openssl`) and the deployment verifier (`python3`) need them.

## 2. Docker Engine (with Compose v2)

Debian's own `docker.io` package works, but the project is tested against Docker CE
from Docker's repository, which also ships the required Compose v2 plugin:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/debian trixie stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Let your user run Docker without sudo, then **log out and back in** (or `newgrp docker`):

```bash
sudo usermod -aG docker "$USER"
```

Verify before continuing — both must work without sudo:

```bash
docker run --rm hello-world
docker compose version        # needs Compose v2.20+
```

## 3. Get the repository

```bash
git clone <YOUR_GITEA_OR_UPSTREAM_URL> dms && cd dms
```

Everything below is run from the repository root unless stated otherwise.

## 4. Development stack (Docker Compose)

### 4.1 Generate local secrets

The stack has **no fallback passwords** — it refuses to start until every secret is
set. Generate a `.env` with strong random values (git-ignored, never committed):

```bash
cd infra/docker-compose
bash generate-env.sh
```

**Write down the two lines it prints** — the demo-user login password and the
Keycloak admin password. They are stored nowhere else except the `.env` file itself.

### 4.2 Start the stack

```bash
docker compose up -d --build --wait
docker compose ps
```

The first build takes several minutes (Maven and npm downloads). `--wait` returns
only when everything is healthy. What comes up, in dependency order:

1. **mongodb** — seeded by `scripts/mongo-init.js` (collections, indexes, root
   folder, default `ITDLZ` department) on first boot.
2. **keycloak** — imports the `dms` realm (roles, clients, demo users).
3. **keycloak-bootstrap** — one-shot job on `python:3.13-slim-trixie`; declares the
   managed `department` user-profile attribute (Keycloak ≥ 24 drops undeclared
   attributes), enforces `department=ITDLZ` on the demo users, verifies the token
   mapper and service-account roles, then exits 0. Idempotent.
4. **backend** — starts only after the bootstrap succeeded and MongoDB is healthy.
5. **frontend**, **ocr-worker**.

### 4.3 Verify the deployment

Run the automated acceptance check — 18 checks covering container health, bootstrap
idempotency, the Keycloak user profile, demo-user departments, the JWT `department`
claim, the department registry, and admin-vs-viewer authorization:

```bash
bash verify-deployment.sh
```

Expected final line: `Result: 18 passed, 0 failed.`

### 4.4 Use the application

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend REST + CMIS + Swagger UI | http://localhost:8081 (`/swagger-ui.html`) |
| Keycloak console | http://localhost:8080 |
| MongoDB (host tools) | `127.0.0.1:27018` |

**Testing from your laptop while the stack runs on a VM:** the Keycloak realm only
allows `localhost` redirect URIs, so open SSH tunnels instead of using the VM's
hostname, then browse `http://localhost:5173` on the laptop:

```bash
ssh -L 5173:localhost:5173 -L 8080:localhost:8080 -L 8081:localhost:8081 <user>@<vm>
```

Demo accounts (password = the `Demo login` value from step 4.1):

- `admin@dms.local` — `dms_admin`, no department, sees the `/admin` console
- `manager@dms.local` — `dms_department_manager`, department `ITDLZ`
- `contributor@dms.local` — `dms_contributor`, department `ITDLZ`
- `viewer@dms.local` — `dms_viewer`, read-only

Suggested first tour: sign in as **admin** → `/admin` shows live Keycloak users,
roles, health, metrics, and the department registry (create a department, assign it
to a user). Sign in as **contributor** → upload a PDF (it receives an EAP file
number, OCR runs in the background) → find it via full-text search. Sign in as
**viewer** → confirm uploads and admin pages are refused.

## 5. Automated tests (all containerized)

**Backend** (unit + integration; integration tests need the Compose MongoDB from
step 4, reachable on 27018 — read the app password from `.env` yourself, don't echo
it into your shell history on shared machines):

```bash
cd infra/docker-compose
MONGO_APP_PASSWORD="$(grep -E '^MONGO_APP_PASSWORD=' .env | cut -d= -f2-)"
docker run --rm --network host \
  -e DMS_TEST_MONGODB_URI="mongodb://dms_app:${MONGO_APP_PASSWORD}@localhost:27018/dms?authSource=dms" \
  -v "$(git rev-parse --show-toplevel)":/repo -w /repo/backend \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp test
```

Expected: **162 tests, 0 failures** (8 skipped — a Testcontainers duplicate of the
Mongo-init tests that cannot reach Docker from inside the Maven container). Without
the `DMS_TEST_MONGODB_URI` override the 54 database-backed tests skip themselves and
only the 108 pure unit tests run.

**Frontend** (Vitest + production build):

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$(git rev-parse --show-toplevel)/frontend":/app -w /app \
  node:lts-trixie-slim bash -c "npm ci && npm test && npm run build"
```

**OCR worker** (pytest):

```bash
docker run --rm -v "$(git rev-parse --show-toplevel)/ocr-worker":/app -w /app \
  python:3.13-slim-trixie bash -c \
  "pip install -q -r requirements.txt -r requirements-dev.txt && pytest -q"
```

## 6. Production-style stack (Kubernetes on minikube)

### 6.1 Install kubectl and minikube

```bash
# kubectl from the official Kubernetes repository
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.32/deb/Release.key \
  | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes.gpg] \
  https://pkgs.k8s.io/core:/stable:/v1.32/deb/ /" \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list > /dev/null
sudo apt update && sudo apt install -y kubectl

# minikube (Docker driver — reuses the Docker Engine from step 2)
curl -fsSLo /tmp/minikube.deb \
  https://storage.googleapis.com/minikube/releases/latest/minikube_latest_amd64.deb
sudo apt install -y /tmp/minikube.deb && rm /tmp/minikube.deb

minikube start --driver=docker
minikube status    # host, kubelet, apiserver: Running
```

If your network uses an HTTP proxy, export `NO_PROXY=192.168.49.0/24,127.0.0.1`
(minikube's subnet) in `~/.bashrc`, or `kubectl` will hang.

### 6.2 Deploy

Three scripts, run from the repository root:

```bash
bash infra/k8s/generate-secrets.sh   # once per cluster; WRITE DOWN what it prints
bash infra/k8s/build-and-load.sh     # builds the Debian 13 images, loads them into minikube
bash infra/k8s/deploy.sh             # applies manifests in order and waits
```

`deploy.sh` deploys MongoDB and Keycloak first, then runs the **keycloak-bootstrap
Job** (same script as Compose, republished as a ConfigMap) and aborts with the Job's
logs if it fails — the backend is only applied after the realm is verified. It ends
with every pod `Running`; the `gitea-runner` pod staying in
`CreateContainerConfigError` is **expected** until step 6.4.

### 6.3 Reach the cluster from your laptop

Get the node IP with `minikube ip`, then tunnel the NodePorts so the `localhost`
URLs and the Keycloak token issuer line up (same reasoning as the Compose tunnels):

```bash
ssh -L 5173:<MINIKUBE_IP>:30573 \
    -L 8080:<MINIKUBE_IP>:30080 \
    -L 8081:<MINIKUBE_IP>:30081 <user>@<vm>
```

Then use `http://localhost:5173` exactly as in section 4.4 — same demo users, with
the password from `generate-secrets.sh` (`demo-user-password`).

Useful checks and demos:

```bash
kubectl -n dms get pods                       # all Running / READY
kubectl -n dms logs job/keycloak-bootstrap    # bootstrap verification output
kubectl -n dms delete pod -l app=backend      # self-healing: replicas keep serving
kubectl -n dms scale deploy/ocr-worker --replicas=3
```

### 6.4 Optional: in-cluster Gitea (repository, CI/CD, OCI registry)

```bash
bash infra/k8s/bootstrap-gitea.sh
```

This mints the Actions runner token and registry pull secret; afterwards the runner
pod recovers on its own. Push the repository into Gitea (NodePort `30300`) and the
CI pipeline in `.gitea/workflows/ci.yml` tests every component, builds the Debian 13
images, pushes them to the OCI registry, and rolls the Deployments. See
`infra/k8s/README.md` and `infra/gitea/README.md` for details.

## 7. Resetting

| Goal | Command |
| --- | --- |
| Fresh Compose stack (wipes data, reruns realm import + Mongo init + bootstrap) | `cd infra/docker-compose && docker compose down -v --remove-orphans && docker compose up -d --build --wait` |
| New Compose secrets | `bash generate-env.sh --force`, then the fresh-stack command above |
| Stop everything, keep data | `docker compose stop` / `minikube stop` |
| Remove the cluster entirely | `minikube delete` (then regenerate secrets on the next cluster: `bash infra/k8s/generate-secrets.sh --force`) |

## 8. Troubleshooting

- **`docker compose up` refuses to start with a missing-variable error** — your
  `.env` predates a newer required key. `bash generate-env.sh --force`, then a fresh
  start with `down -v` (the old volumes were initialized with the old passwords).
- **A port is already in use (5173/8080/8081/9000/27018)** — another service or an
  old tunnel holds it: `ss -ltnp | grep <port>`. MongoDB's host port can be moved
  with `MONGO_HOST_PORT` in `.env`.
- **Demo users can't log in / department missing from the token** — read
  `docker compose logs keycloak-bootstrap` (Compose) or
  `kubectl -n dms logs job/keycloak-bootstrap` (k8s); the bootstrap names the exact
  failed check. Re-running it is always safe:
  `docker compose run --rm keycloak-bootstrap`.
- **Login redirect fails when testing from a laptop** — you used the VM's hostname.
  Only `http://localhost:5173` is an allowed redirect origin; open the SSH tunnels.
- **k8s pod in `ImagePullBackOff`** — the image never reached minikube. Re-run
  `bash infra/k8s/build-and-load.sh` and check `minikube image ls | grep dms-`.
- **`kubectl` hangs** — proxy environment lost; `source ~/.bashrc` (see 6.1).
- **Backend not READY in k8s** — usually waiting for MongoDB:
  `kubectl -n dms logs mongodb-0 | grep -i "DMS database initialized"`.
- **`./mvnw` fails on a fresh clone** — the wrapper's `.mvn` directory is
  git-ignored; use the containerized Maven command from section 5 (or a system
  `mvn`, as CI does).

## 9. Course-requirement checklist → how to verify it here

| Requirement | Where it lives | Proof on this machine |
| --- | --- | --- |
| Central document archive with upload, folders, EAP file numbers | Spring Boot backend, MongoDB/GridFS | Upload in the UI; number appears on the document page |
| Full-text search incl. OCR text | `ocr-worker`, `/api/search` | Upload a scanned PDF, search its content after OCR completes |
| Role-based access control via central IAM | Keycloak realm + backend RBAC resolver | `verify-deployment.sh` (admin 201 vs viewer 403); backend test suite (162 tests) |
| Department model on users, tokens, and ACLs | Keycloak `department` attribute + JWT claim + Mongo registry | `/admin` department UI; JWT claim check in `verify-deployment.sh` |
| Standard interface (CMIS) | `/cmis` browser binding | `GET http://localhost:8081/cmis/browser` with a Bearer token |
| Dev deployment: Docker Compose | `infra/docker-compose/` | Section 4 |
| Prod deployment: Kubernetes with health checks, replicas, persistence | `infra/k8s/` | Section 6; self-healing demos |
| All self-built images on Debian 13 | every `Dockerfile`, bootstrap runtime | `grep -i trixie */Dockerfile infra/*/Dockerfile*` |
| Self-hosted Git + CI/CD + OCI registry | Gitea + Actions (`.gitea/workflows/ci.yml`) | Section 6.4 |
| Automated tests for every component | `backend/src/test`, `frontend/src/**/*.test.*`, `ocr-worker/tests` | Section 5 |
| Reproducible from a clean clone with generated secrets | `generate-env.sh` / `generate-secrets.sh`, no committed credentials | This entire guide |
