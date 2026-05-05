# ── Build stage ───────────────────────────────────────────────────────────────
# maven:3-eclipse-temurin-21 is based on Ubuntu/Debian — use trixie-based image
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache dependency layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Production stage (Debian 13 trixie + JRE) ─────────────────────────────────
FROM debian:trixie-slim AS prod

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -sf http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
