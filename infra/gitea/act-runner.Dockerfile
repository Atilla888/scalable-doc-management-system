# Gitea Actions runner, built on Debian 13 (trixie). It executes the CI/CD
# workflow in "host" mode, the steps run directly in this container, which is why
# the whole toolchain (JDK 21 + Maven, Node.js, Python, the Docker CLI, kubectl) is
# baked in. Image builds are delegated to a Docker-in-Docker sidecar over
# DOCKER_HOST, so the runner needs only the Docker client, not the engine.
FROM debian:trixie-slim

ARG ACT_RUNNER_VERSION=0.2.11
ARG DOCKER_CLI_VERSION=27.5.1
ARG KUBECTL_VERSION=1.32.3
ARG MAVEN_VERSION=3.9.9
ARG TARGETARCH=amd64

ENV DEBIAN_FRONTEND=noninteractive
ENV PIP_BREAK_SYSTEM_PACKAGES=1

# Toolchain: git + build tools, JDK 21 (backend), Node.js (frontend),
# Python 3 (OCR worker tests), and the utilities the workflow shells out to.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        bash \
        openjdk-21-jdk-headless \
        nodejs \
        npm \
        python3 \
        python3-pip \
        python3-venv \
        tesseract-ocr \
        poppler-utils \
        xz-utils \
        tar \
    && rm -rf /var/lib/apt/lists/*

# Maven (backend uses the wrapper, but a system Maven is a useful fallback).
RUN curl -fsSL "https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        -o /tmp/maven.tar.gz \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn \
    && rm /tmp/maven.tar.gz

# Static Docker CLI (talks to the dind sidecar; no engine installed here).
RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_CLI_VERSION}.tgz" \
        -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz -C /tmp \
    && install -m 0755 /tmp/docker/docker /usr/local/bin/docker \
    && rm -rf /tmp/docker /tmp/docker.tgz

# kubectl for the deploy step (uses the in-cluster ServiceAccount at runtime).
RUN curl -fsSL "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" \
        -o /usr/local/bin/kubectl \
    && chmod 0755 /usr/local/bin/kubectl

# act_runner itself.
RUN curl -fsSL "https://dl.gitea.com/act_runner/${ACT_RUNNER_VERSION}/act_runner-${ACT_RUNNER_VERSION}-linux-${TARGETARCH}" \
        -o /usr/local/bin/act_runner \
    && chmod 0755 /usr/local/bin/act_runner \
    && act_runner --version

COPY act-runner-entrypoint.sh /usr/local/bin/act-runner-entrypoint.sh
RUN chmod 0755 /usr/local/bin/act-runner-entrypoint.sh

# The registration state (.runner) and workspace live under /data (a PVC), so the
# runner registers once and survives restarts.
ENV DOCKER_HOST=tcp://localhost:2375
WORKDIR /data
ENTRYPOINT ["/usr/local/bin/act-runner-entrypoint.sh"]
