# DMS backend

Spring Boot 3.4 on Java 21. The API validates Keycloak JWTs as an OAuth2 resource server, exposes authenticated routes under `/api/**`, and keeps health checks public for Docker and Kubernetes probes.

## Run with Docker Compose

The usual way to develop is from the repository root: start the full stack with `docker compose` under `infra/docker-compose` (see the root README). The backend listens on port 8081.

Inside Compose, tokens from the browser use issuer `http://localhost:8080/realms/dms`, while the JVM fetches signing keys from `http://keycloak:8080/.../certs` via `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` so signature verification works across hostnames.

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

Demo users and passwords are documented in the root README.

## Tests

You do not need Maven installed globally. From this directory:

```bash
mvnw.cmd test
```

On Linux or macOS: `./mvnw test`. Tests cover JWT role extraction, missing or invalid tokens (401), public `/health`, and admin route protection. They use a test `JwtDecoder` so Keycloak does not need to run during `mvn test`.
