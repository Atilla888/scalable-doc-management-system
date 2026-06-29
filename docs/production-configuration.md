# Production configuration

Production must use the Spring `prod` profile and a separately managed Keycloak realm. The local
Compose stack and `infra/keycloak/dms-realm.json` are development assets; they intentionally contain
demo identities and must not be deployed.

## Required runtime configuration

Use `infra/docker-compose/.env.prod.example` as a variable inventory, not as a secret file to commit
or deploy. Store credential values in Kubernetes Secrets, the platform secret manager, or an
equivalent encrypted store and expose them to the containers at runtime.

Start the backend with:

```text
SPRING_PROFILES_ACTIVE=prod
```

The production profile requires:

- `KEYCLOAK_ISSUER_URI` — public HTTPS issuer exactly matching the JWT `iss` claim.
- `KEYCLOAK_JWK_SET_URI` — Keycloak certificate endpoint reachable by the backend.
- `MONGODB_URI` — authenticated MongoDB connection string supplied as a secret.
- `DMS_KEYCLOAK_ADMIN_BASE_URL` — Keycloak URL reachable by the backend.
- `DMS_KEYCLOAK_ADMIN_CLIENT_SECRET` — confidential service-account secret.
- `DMS_CORS_ALLOWED_ORIGINS` — comma-separated exact frontend origins.
- `VITE_API_BASE_URL` and `VITE_KEYCLOAK_URL` — public HTTPS endpoints embedded in the frontend
  build.
- `VITE_SHOW_DEMO_USERS=false` — removes all development-account hints from the login page.

Upload limits default to 25 MB per file and 26 MB per multipart request in production. Override
`DMS_MAX_FILE_SIZE`, `DMS_MAX_REQUEST_SIZE`, and `DMS_ALLOWED_UPLOAD_TYPES` only after reviewing the
storage, reverse-proxy, and malware-scanning limits. Do not configure a wildcard CORS origin,
especially while credentialed requests are enabled.

## Keycloak

`infra/keycloak/dms-realm-prod.json.example` is the production-safe realm template. It defines DMS
roles, the public PKCE frontend client, and the read-only backend directory client. It contains no
human or demo users. Resolve `DMS_FRONTEND_URL` and `DMS_KEYCLOAK_ADMIN_CLIENT_SECRET` from deployment
configuration, then copy it to a `*-realm.json` file in the production import directory. The
`.json.example` suffix prevents Keycloak from importing the template accidentally.

For a production Keycloak installation:

1. Run Keycloak in production mode behind HTTPS with an explicit hostname and strict hostname
   validation. Do not use `start-dev`.
2. Supply initial bootstrap administrator credentials from a secret store only for first-time
   provisioning. Remove those variables after the permanent administrator exists.
3. Use a persistent supported database and back it up. Do not rely on the container filesystem.
4. Import the production realm template or reproduce it through managed infrastructure configuration.
5. Keep the `dms-admin-api` service account limited to `query-users`, `view-users`, and `view-realm`.
   Rotate its secret and update the backend secret atomically.
6. Provision human users through the organization's identity provider or controlled administration.
   Assign one DMS realm role and the `department` attribute where applicable.
7. Restrict frontend redirect URIs and web origins to the deployed HTTPS origin.

The browser never receives the directory client secret. AdminPage calls the backend with the user's
Bearer token; `/api/admin/**` requires `dms_admin`, and the backend performs the Keycloak directory
lookup server-side.

## Deployment checks

Before release:

- Confirm the production realm contains no development identities.
- Confirm a non-admin token receives `403` from `GET /api/admin/overview`.
- Confirm an admin sees current Keycloak users, roles, departments, and component health.
- Confirm requests from an origin outside `DMS_CORS_ALLOWED_ORIGINS` receive no CORS permission.
- Upload a permitted small file, then verify an oversized file returns `413` and a mismatched file
  signature returns `415`.
- Confirm Actuator health details are not publicly disclosed in the production profile.
- Search the deployment manifests and generated documentation for embedded passwords or client
  secrets before publishing.
