FROM debian:trixie-slim AS builder

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        maven openjdk-21-jdk-headless ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM debian:trixie-slim AS prod

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home --uid 10001 appuser

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER appuser

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -sf http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
