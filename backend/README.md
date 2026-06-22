# DMS backend

Spring Boot 3.4 on Java 21. The API validates Keycloak JWTs as an OAuth2 resource server, exposes authenticated routes under `/api/**`, and keeps health checks public for Docker and Kubernetes probes.

## Run with Docker Compose

The usual way to develop is from the repository root: start the full stack with `docker compose` under `infra/docker-compose` (see the root README). The backend listens on port 8081 and connects to MongoDB at `mongodb://mongodb:27017/dms`.

On first start with an empty volume, `scripts/mongo-init.js` creates the `dms` database (collections, indexes, root folder). Inside Compose, JWT issuer is `http://localhost:8080/realms/dms` while JWKS is fetched from `http://keycloak:8080/.../certs`.

## Run the API alone

Keycloak must already be running (for example via Compose). From this directory:

```bash
mvnw.cmd spring-boot:run
```

On Linux or macOS use `./mvnw spring-boot:run`. If Keycloak is not on localhost:8080, set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` and `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` before starting.

## HTTP surface

`GET /health` returns `{"status":"ok"}` and does not require authentication. Use it for simple liveness checks.

`GET /actuator/health` is also public and returns Spring Actuator health details (the Dockerfile healthcheck uses this path).

`GET /api/auth/me` requires a valid Bearer token. It echoes identity from the JWT: subject, preferred username, email, realm roles, and `department` when present.

Any other path under `/api/**` requires authentication. Paths under `/api/admin/**` additionally require the `dms_admin` realm role. Spring maps Keycloak roles from `realm_access.roles` to authorities without a `ROLE_` prefix, so security expressions use `hasAuthority("dms_admin")`, not `hasRole(...)`.

### Folders and search

All routes require a Bearer token.

- `GET /api/folders` — paginated list of child folders, filtered to those the caller may **read**. Query params: `parentId` (optional — children of the root folder when omitted), `page` (default `0`), `size` (default `20`, max `100`). Response: `{ "content": [ { "id", "name", "path" } ], "page", "size", "totalElements", "totalPages" }`. 404 if `parentId` does not reference an existing folder.
- `POST /api/folders` — JSON body `{ "name" (required), "parentId" (optional — root when omitted), "inheritFromParent" (optional, default `true`) }`. Requires **create** permission on the parent folder. Generates `_id`, the materialized `path` (parent path + name + `/`, e.g. `/Finance/` + `2026` → `/Finance/2026/`), and `created_at`; seeds the ACL from the caller's identity (`owner`, `owner_department`). Returns `201 Created` with the new folder document. 404 if the parent does not exist; 409 if a sibling with the same name already exists (this also prevents creating a second root).
- `GET /api/folders/root` — root folder view: folder metadata, breadcrumb, subfolders, and active documents.
- `GET /api/folders/{id}` — same shape for any folder; 404 if the folder does not exist.
- `GET /api/search?q=...` — searches active documents by title, description, EAP number, and OCR text (case-insensitive), returning id, title, EAP number, type, OCR status, and a snippet.

### Documents (multipart upload)

All document routes require a Bearer token.

- `POST /api/documents` — multipart fields: `file`, `title`, `documentType`, `parentId` (required); optional `description`, `eapCategory`, `inheritFromParent`. Requires **create** permission on the parent folder. Stores the binary in GridFS, metadata in `documents`, and allocates an EAP number via `eap_sequences`. Response: `{ "id", "status": "UPLOADED", "ocrStatus" }`.
- `GET /api/documents/{id}` — metadata only. Requires **read**.
- `GET /api/documents/{id}/download` — streams the GridFS file. Requires **read**.
- `PUT /api/documents/{id}/metadata` — update `title`/`description`/`documentType`. Requires **update**.
- `DELETE /api/documents/{id}` — soft-delete (`document_status = deleted`); GridFS file is kept in MVP. Requires **delete**.
- `GET /api/documents/{id}/permissions` — requires **read**; the full ACL is only returned to `dms_admin` or users with **managePermissions** (others get their effective permissions only).
- `PUT /api/documents/{id}/permissions` — replace the document ACL. Requires **managePermissions** (or `dms_admin`).

### RBAC model

`PermissionService` resolves every document/folder action in this order:

1. `dms_admin` → always allow.
2. Resource **owner** → all actions.
3. `dms_department_manager` whose department matches the resource's department → all actions.
4. Direct **ACL** membership (user id / role / department) gated by the ACL's access flags (`read`, `create`, `update`, `delete`, `managePermissions`).
5. Inherited ACL: when `inheritFromParent = true`, walk up the parent folder chain and re-evaluate steps 2–4.
6. `dms_contributor` may **create** inside any folder it can **read**.
7. Otherwise → **deny** (HTTP `403`).

A request for a document that exists but the user cannot read returns `403` (never `404`), so document existence is not leaked. Folder views (`GET /api/folders/*`) require **read** on the folder and filter subfolders/documents to those the caller may read.

Demo users and passwords are documented in the root README.

## Tests

You do not need Maven installed globally. From this directory:

```bash
mvnw.cmd test
```

On Linux or macOS: `./mvnw test`. Tests cover JWT role extraction, missing or invalid tokens (401), public `/health`, admin route protection, MongoDB initialization, EAP number generation, document upload/download/delete, and RBAC enforcement. The RBAC resolver matrix and inheritance walk are covered by `PermissionServiceTest` (pure unit, no MongoDB), and `403` enforcement (unauthorized read/download, unauthorized permission/metadata update) is covered by `DocumentApiIntegrationTest` against Compose MongoDB.

MongoDB tests: `MongoInitializationLocalTest` connects to `mongodb://localhost:27017/dms` while Compose is running (recommended on Windows). `MongoInitializationTest` uses Testcontainers when the JVM can reach Docker; if those six tests are skipped but `docker ps` works, use the local test — Docker CLI and Testcontainers use different APIs on some Docker Desktop versions.
