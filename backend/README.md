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

Demo users and passwords are documented in the root README.

## Tests

You do not need Maven installed globally. From this directory:

```bash
mvnw.cmd test
```

On Linux or macOS: `./mvnw test`. Tests cover JWT role extraction, missing or invalid tokens (401), public `/health`, admin route protection, and MongoDB initialization.

MongoDB tests: `MongoInitializationLocalTest` connects to `mongodb://localhost:27017/dms` while Compose is running (recommended on Windows). `MongoInitializationTest` uses Testcontainers when the JVM can reach Docker; if those six tests are skipped but `docker ps` works, use the local test — Docker CLI and Testcontainers use different APIs on some Docker Desktop versions.
