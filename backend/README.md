# DMS Backend

Spring Boot REST API for the Document Management System.

## Stack

- Java 21
- Spring Boot 3.4
- Spring Security OAuth2 Resource Server (Keycloak JWT)
- Maven

## Running locally (via Docker Compose)

```bash
cd infra/docker-compose
docker compose up --build
```

Backend will be available at `http://localhost:8081`.

## Running standalone (requires Keycloak running)

```bash
cd backend
mvn spring-boot:run
```

Set the Keycloak issuer URI if Keycloak runs on a different host:

```bash
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8080/realms/dms \
  mvn spring-boot:run
```

## Key endpoints

| Method | Path              | Access             |
|--------|-------------------|--------------------|
| GET    | /api/auth/me      | Any authenticated  |
| GET    | /api/admin/**     | DMS_ADMIN only     |
| GET    | /actuator/health  | Public             |

## Security model

All endpoints under `/api/**` require a valid Keycloak JWT bearer token.

The `SecurityConfig` extracts roles from the `realm_access.roles` claim in the JWT
and maps them to Spring `GrantedAuthority` objects without a `ROLE_` prefix,
so use `.hasAuthority("DMS_ADMIN")` rather than `.hasRole("DMS_ADMIN")`.

## Demo users

| Username | Password    | Role                    |
|----------|-------------|-------------------------|
| admin    | admin123    | DMS_ADMIN               |
| manager  | manager123  | DMS_DEPARTMENT_MANAGER  |
| editor   | editor123   | DMS_DOCUMENT_EDITOR     |
| viewer   | viewer123   | DMS_DOCUMENT_VIEWER     |
| auditor  | auditor123  | DMS_AUDITOR             |
