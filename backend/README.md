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

### Administration

- `GET /api/admin/overview` — admin-only aggregate used by the AdminPage. Users, enabled state, realm roles, and departments come from the live Keycloak Admin API. Document/folder counts and MongoDB status come from the live database. Permission scopes mirror the actions enforced by `PermissionService`.
- `GET /api/admin/ocr` and `POST /api/admin/ocr/{id}/retry` — list and retry OCR jobs.

#### Department registry

Departments live in the MongoDB `departments` collection (`id`, `code`, `displayName`, `active`, `createdAt`, `updatedAt`). The normalized `code` (trimmed, upper-cased, `A-Z0-9_-`, 2–32 chars, unique) is the value carried by Keycloak user attributes, JWT `department` claims, and folder/document ACLs, so it is **immutable after creation** — rename the display name or deactivate instead. `ITDLZ` is seeded idempotently (by `scripts/mongo-init.js` on a fresh database and again by the backend at startup).

- `GET /api/admin/departments` — list the registry, ordered by code.
- `POST /api/admin/departments` — body `{ "code", "displayName"? }`. Returns `201`; a malformed code is `400`, a duplicate is `409`.
- `PUT /api/admin/departments/{code}` — body `{ "displayName"?, "active"? }` (nulls keep current values). The code itself cannot be changed.
- `DELETE /api/admin/departments/{code}` — `204` only while nothing references the code; if users, folders, documents, or ACLs still carry it the response is `409` (deactivate instead).
- `PUT /api/admin/users/{userId}/department` — body `{ "department": "CODE" }` assigns, `{ "department": null }` removes. Only existing (`400` otherwise) and active (`409` otherwise) departments can be assigned; the change is written to the user's Keycloak `department` attribute via the `dms-admin-api` service account, so it appears in freshly issued tokens.

The backend uses the confidential `dms-admin-api` service account for directory access and for maintaining the users' `department` attribute. It holds only the least-privilege `realm-management` roles `query-users`, `view-users`, `view-realm`, and `manage-users` — not `realm-admin`. Its secret is supplied through `DMS_KEYCLOAK_ADMIN_CLIENT_SECRET`; it must never be exposed through frontend variables.

### Folders and search

All routes require a Bearer token.

- `GET /api/folders` — paginated list of child folders, filtered to those the caller may **read**. Query params: `parentId` (optional — children of the root folder when omitted), `page` (default `0`), `size` (default `20`, max `100`). Response: `{ "content": [ { "id", "name", "path" } ], "page", "size", "totalElements", "totalPages" }`. 404 if `parentId` does not reference an existing folder.
- `POST /api/folders` — JSON body `{ "name" (required), "parentId" (optional — root when omitted), "inheritFromParent" (optional, default `true`) }`. Requires **create** permission on the parent folder. Generates `_id`, the materialized `path` (parent path + name + `/`, e.g. `/Finance/` + `2026` → `/Finance/2026/`), and `created_at`; seeds the ACL from the caller's identity (`owner`, `owner_department`). Returns `201 Created` with the new folder document. 404 if the parent does not exist; 409 if a sibling with the same name already exists (this also prevents creating a second root).
- `PATCH /api/folders/{id}` — JSON body `{ "name"?, "parentId"? }`. Renames (`name`) and/or moves (`parentId`) a folder. Requires **update** on the folder, plus **create** on the destination parent when moving. The materialized `path` of the folder **and its whole subtree** is rewritten. The root folder cannot be modified; a folder cannot be moved into itself or a descendant (400); a name clash at the destination is 409. Returns the updated folder document.
- `DELETE /api/folders/{id}?recursive=false` — requires **delete** on the folder; the root folder can never be deleted. A non-empty folder returns 409 unless `recursive=true`, which removes the whole subtree and soft-deletes the documents it contains. Returns `204 No Content`.
- `GET /api/folders/tree` — flat list (`{ id, name, path }`) of every folder the caller can **read**, ordered by path. Used by the UI as the destination picker when moving a folder.
- `GET /api/folders/root` — root folder view: folder metadata, breadcrumb, subfolders, and active documents.
- `GET /api/folders/{id}` — same shape for any folder; 404 if the folder does not exist.
- `GET /api/search` — full-text search over **active, indexed** documents (title, description, OCR text), with RBAC applied **inside** the query (never post-filtered). Params: `query` (or `q`); optional filters `type` (document type), `department` (organizational unit), `folder` (folder id), `status` (OCR status), `dateFrom`/`dateTo` (`yyyy-MM-dd`, inclusive); `sort` (`date_desc` default, `date_asc`, `title_asc`, `title_desc`); `page` (default 0), `limit` (default 20, max 100). Returns a paged object `{ content: [ { id, title, eapNumber, documentType, snippet, parentFolderId, updatedAt, ocrStatus } ], page, limit, totalElements, totalPages, hasMore }`. The `snippet` is centred on the matched term so OCR/description hits are shown in context (the client highlights the term). A search with neither a term nor any filter returns an empty page rather than dumping the whole archive.

### Documents (multipart upload)

All document routes require a Bearer token.

- `POST /api/documents` — multipart fields: `file`, `title`, `documentType`, `parentId` (required); optional `description`, `eapCategory`, `inheritFromParent`. Requires **create** permission on the parent folder. Stores the binary in GridFS, metadata in `documents`, and allocates an EAP number via `eap_sequences`. Response: `{ "id", "status": "UPLOADED", "ocrStatus" }`.
- `GET /api/documents/{id}` — metadata only. Requires **read**.
- `GET /api/documents/{id}/download` — streams the GridFS file. Requires **read**.
- `PUT /api/documents/{id}/metadata` — update `title`/`description`/`documentType`. Requires **update**.
- `DELETE /api/documents/{id}` — soft-delete (`document_status = deleted`); GridFS file is kept in MVP. Requires **delete**.
- `GET /api/documents/{id}/permissions` — requires **read**; the full ACL is only returned to `dms_admin` or users with **managePermissions** (others get their effective permissions only).
- `PUT /api/documents/{id}/permissions` — replace the document ACL. Requires **managePermissions** (or `dms_admin`).

Upload size is limited before controller execution and checked again by the document service. Accepted
types are configured with `DMS_ALLOWED_UPLOAD_TYPES`; the default allow-list is PDF, PNG, JPEG, plain
text, and DOCX. The validator requires the declared MIME type, filename extension, and file signature
to agree. Invalid types return `415`, oversized files return `413`, and filenames containing path
separators are rejected.

### CMIS interface (Browser / JSON binding)

A minimal CMIS 1.1 interface is exposed under `/cmis`, using the **Browser (JSON) binding**. We chose the JSON binding (rather than AtomPub or the OpenCMIS server framework) because Apache Chemistry OpenCMIS still targets `javax.servlet` and is incompatible with this Spring Boot 3 / Tomcat 10 (`jakarta.servlet`) stack; the JSON binding maps cleanly onto Spring controllers and reuses the existing JWT auth and `PermissionService` RBAC unchanged.

Conventions: reads are dispatched by the `cmisselector` query parameter (GET); writes by the `cmisaction` form parameter (POST). All `/cmis/**` requests require a Keycloak Bearer token (unauthenticated → `401`). Faults are returned as the Browser binding's JSON body `{"exception": "...", "message": "..."}` with the matching HTTP status — notably `permissionDenied` → `403`, `objectNotFound` → `404`, `invalidArgument` → `400`, `constraint`/`nameConstraintViolation` → `409`. Object types map as `cmis:folder` ↔ `folders` and `cmis:document` ↔ `documents`. Repository identity is configurable via `dms.cmis.repository-id` / `dms.cmis.repository-name`.

Service URLs:

- `GET /cmis/browser` — getRepositories (map keyed by repository id, each value a repository info object with `repositoryId`, `repositoryName`, `cmisVersionSupported`, `rootFolderId`).
- `GET /cmis/browser/{repoId}?cmisselector=repositoryInfo` — getRepositoryInfo for one repository.
- `GET /cmis/browser/{repoId}?cmisselector=query&statement=...` — query (also available via `cmisselector=query` on `/root`, or POST `cmisaction=query`). Supports the MVP subset `CONTAINS('keyword')` (full-text, identical RBAC path to `/api/search`) and `WHERE cmis:name = '...'`. Both apply the same per-user permission filter as REST search.
- `GET /cmis/browser/{repoId}/root?cmisselector=object&objectId=X` — getObject (folder or document; unauthorized → `permissionDenied`). `objectId` omitted ⇒ root folder.
- `GET /cmis/browser/{repoId}/root?cmisselector=children&objectId=X` — getChildren (only objects the caller may **read**; `maxItems`/`skipCount` paginate).
- `GET /cmis/browser/{repoId}/root?cmisselector=parents&objectId=X` — getParents (parent folder of an object).
- `GET /cmis/browser/{repoId}/root?cmisselector=content&objectId=X` — getContentStream (streams the GridFS blob).
- `POST /cmis/browser/{repoId}/root` with `cmisaction`:
  - `createFolder` — `objectId` = parent (root if omitted), `cmis:name` via `propertyId[n]`/`propertyValue[n]`. Requires **create** on the parent. Returns the new `cmis:folder`.
  - `createDocument` — multipart `content` part + `objectId` parent + `cmis:name` (optional `dms:documentType`). Requires **create**. The document is created through the same path as `POST /api/documents`, so it is immediately visible (with identical metadata) via the REST API.
  - `setContent` — multipart `content` + `objectId`. Replaces the document's binary. Requires **update**.
  - `delete` — `objectId`. Documents are soft-deleted; folders require **delete** and must be empty and non-root (deleteTree is out of scope) else `constraint`.

A `dms_viewer` (read-only) caller is rejected with `permissionDenied` on every write (`createDocument`, `createFolder`, `setContent`, `delete`), exactly as the REST API rejects the same actions.

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

MongoDB-backed integration tests (document/folder/search RBAC, CMIS, EAP numbers, Mongo init) gate themselves on a reachable, seeded MongoDB and self-skip otherwise (each gated class then reports `Tests run: 0`). They connect to `mongodb://localhost:27017/dms` without credentials by default — exactly the throwaway database the CI pipeline provides. To run them against the **Compose** MongoDB instead (published on `127.0.0.1:27018` with authentication), point them at it explicitly:

```bash
# from infra/docker-compose: docker compose up -d --wait mongodb
cd backend
DMS_TEST_MONGODB_URI="mongodb://dms_app:<MONGO_APP_PASSWORD from infra/docker-compose/.env>@localhost:27018/dms?authSource=dms" \
  mvn test    # or ./mvnw test where the wrapper is set up
```

`MongoInitializationTest` additionally spins up its own `mongo:7` via Testcontainers and is skipped when the JVM cannot reach Docker (`@Testcontainers(disabledWithoutDocker = true)`); `MongoInitializationLocalTest` covers the same assertions against the configured local database.
