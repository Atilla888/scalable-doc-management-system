# Scalable Central Document Management System (DMS)

This repository is the course project for a cloud-native document archive aimed at public authorities. Authorities share one platform to store, organize, search, and retrieve digital documents. Access is enforced with role-based permissions, full-text search (including OCR text from scans), Keycloak login, a Spring Boot API, a React frontend, MongoDB for data, Docker Compose for local development, and Kubernetes with Gitea planned for production.

## Run the stack locally

You do not need to configure Keycloak by hand. On startup, Docker Compose imports the `dms` realm from `infra/keycloak/dms-realm.json`.

From the repository root:

```bash
cd infra/docker-compose
cp .env.example .env       # first time only
docker compose down -v
docker compose up --build
```

After containers are healthy, use the frontend at http://localhost:5173, the API at http://localhost:8081, Keycloak at http://localhost:8080, and MongoDB on port 27017. The init script `scripts/mongo-init.js` runs automatically on first boot (empty volume) and creates collections, indexes, and the root folder `/`.

The frontend reads all URLs from `VITE_*` environment variables (`VITE_API_BASE_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`) — see `frontend/.env.example`. Compose sets these for you; for standalone `npm run dev`, copy `.env.example` to `.env.local`.

## Main workflow (frontend)

After signing in through Keycloak, the app exposes the core document workflow:

- `/` — Dashboard: lists the root folder's subfolders and documents.
- `/folders/:id` — Folder view with breadcrumb navigation; lists subfolders and documents the backend allows you to see.
- `/documents/:id` — Document detail: metadata, OCR status (auto-refreshes while pending or processing), and a download button. A 403 from the backend shows a clear access-denied message instead of content.
- `/upload` — Upload form: file picker plus title, document type, parent folder, and department; submits to `POST /api/documents` and shows the resulting OCR status.
- `/search` — Full-text search over titles, EAP numbers, and OCR text; results link to document detail.

Every API call attaches the Keycloak Bearer token. The UI never hides content with CSS — it renders exactly what the backend returns and relies on 401/403 responses.

The first `down -v` wipes old container data so the realm import and MongoDB initialization run cleanly on a fresh machine.

## Demo accounts (development only)

Every demo user has the password `changeme_dev`. Use it only locally; never deploy that password to production.

- **admin@dms.local** — role `dms_admin`, no department attribute.
- **manager@dms.local** — role `dms_department_manager`, department `ITDLZ`.
- **contributor@dms.local** — role `dms_contributor`, department `ITDLZ`.
- **viewer@dms.local** — role `dms_viewer`, department `ITDLZ`.

Sign in through the frontend (you will be redirected to Keycloak) or through the Keycloak login page on port 8080.

## Check that authentication works

Log in as viewer@dms.local, open the browser developer tools, and copy the `access_token` from the Keycloak `token` response in the Network tab. Call the backend:

```http
GET http://localhost:8081/api/auth/me
Authorization: Bearer <paste access_token here>
```

A successful response includes your username and `realm_access.roles` with `dms_viewer`. Manager, contributor, and viewer tokens should also include `"department": "ITDLZ"` when the realm mapper is active.

Public health check without a token:

```http
GET http://localhost:8081/health
```

Expected body: `{"status":"ok"}`.

## Repository layout

- `backend/` — Spring Boot API and security tests.
- `frontend/` — React + Vite UI.
- `ocr-worker/` — standalone Tesseract OCR worker that makes scans searchable (see `ocr-worker/README.md`).
- `infra/keycloak/` — realm export for automatic import.
- `infra/docker-compose/` — local development compose file.
- `scripts/` — database initialization helpers.

To verify MongoDB after a fresh start: `docker compose exec mongodb mongosh dms --eval "db.folders.getIndexes()"`, then `db.folders.findOne({ path: '/' })`.

Backend-specific build and test instructions are in `backend/README.md`.
