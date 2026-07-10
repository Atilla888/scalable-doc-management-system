# Gitea server, built on Debian 13 (trixie) to satisfy the project's
# "all images we build ourselves are Debian 13" rule. Gitea's own image is
# Alpine-based, so instead of using it we install the official release binary
# on top of Debian. Gitea provides three things the project requires:
#   1. hosts the Git repository,
#   2. runs the build/rollout pipeline via Gitea Actions,
#   3. serves the container images as an OCI registry (Gitea Packages).
#
# SQLite is used as Gitea's own metadata store (kept on the PVC) so the
# infrastructure needs no extra database.
FROM debian:trixie-slim

ARG GITEA_VERSION=1.23.8
ARG TARGETARCH=amd64

# git + git-lfs are needed by Gitea to serve repositories; the rest are runtime deps.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        git-lfs \
        curl \
        gettext-base \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# Dedicated unprivileged account. UID/GID 1000 owns the data volume.
RUN groupadd -g 1000 git \
    && useradd -m -u 1000 -g git -s /bin/bash git

# Fetch and verify the official Gitea binary.
RUN curl -fsSL "https://dl.gitea.com/gitea/${GITEA_VERSION}/gitea-${GITEA_VERSION}-linux-${TARGETARCH}" \
        -o /usr/local/bin/gitea \
    && chmod 0755 /usr/local/bin/gitea \
    && gitea --version

# Data (/var/lib/gitea) and config (/etc/gitea) are mounted from the PVC at runtime.
RUN mkdir -p /var/lib/gitea/custom /var/lib/gitea/data /var/lib/gitea/log /etc/gitea \
    && chown -R git:git /var/lib/gitea /etc/gitea \
    && chmod 0750 /etc/gitea

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod 0755 /usr/local/bin/entrypoint.sh

ENV GITEA_WORK_DIR=/var/lib/gitea
ENV GITEA_CUSTOM=/var/lib/gitea/custom
ENV USER=git

USER git
EXPOSE 3000 2222
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
