# Scalable Central Document Management System (DMS)

This repository is the course project for a cloud-native document archive aimed at public authorities. Authorities share one platform to store, organize, search, and retrieve digital documents. Access is enforced with role-based permissions, full-text search (including OCR text from scans), Keycloak login, a Spring Boot API, a React frontend, MongoDB for data, Docker Compose for local development, and Kubernetes with Gitea planned for production.

## Run the stack locally

You do not need to configure Keycloak by hand. On startup, Docker Compose imports the `dms` realm from `infra/keycloak/dms-realm.json`.

From the repository root:

```bash
cd infra/docker-compose
docker compose down -v
docker compose up --build
```

After containers are healthy, use the frontend at http://localhost:5173, the API at http://localhost:8081, and Keycloak at http://localhost:8080. The first `down -v` wipes old container data so the realm import runs cleanly on a fresh machine.

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
- `infra/keycloak/` — realm export for automatic import.
- `infra/docker-compose/` — local development compose file.
- `scripts/` — database initialization helpers.

Backend-specific build and test instructions are in `backend/README.md`.
