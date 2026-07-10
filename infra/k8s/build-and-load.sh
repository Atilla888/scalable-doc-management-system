#!/usr/bin/env bash
# Build our three images (backend, frontend, OCR worker) and load them into minikube,
# along with the Mongo and Keycloak images, so the cluster never has to pull anything
# from an external registry.
#
# Behind the uni proxy the builds need it to reach apt/maven/npm/pip. http_proxy and
# friends are predefined Docker build args, so passing them with --build-arg is enough,
# no Dockerfile changes.
#
# Run from anywhere:  bash infra/k8s/build-and-load.sh
set -euo pipefail

# Work out the repo root from where this script lives.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Forward the proxy into the builds if it's set.
PROXY_ARGS=()
if [[ -n "${http_proxy:-}" ]]; then
  PROXY_ARGS+=(--build-arg "http_proxy=${http_proxy}" --build-arg "HTTP_PROXY=${http_proxy}")
fi
if [[ -n "${https_proxy:-}" ]]; then
  PROXY_ARGS+=(--build-arg "https_proxy=${https_proxy}" --build-arg "HTTPS_PROXY=${https_proxy}")
fi
if [[ -n "${no_proxy:-}" ]]; then
  PROXY_ARGS+=(--build-arg "no_proxy=${no_proxy}" --build-arg "NO_PROXY=${no_proxy}")
fi

echo ">> Building dms-backend:local"
docker build "${PROXY_ARGS[@]}" --target prod -t dms-backend:local "$ROOT/backend"

echo ">> Building dms-frontend:local"
docker build "${PROXY_ARGS[@]}" --target prod -t dms-frontend:local "$ROOT/frontend"

echo ">> Building dms-ocr-worker:local"
docker build "${PROXY_ARGS[@]}" -t dms-ocr-worker:local "$ROOT/ocr-worker"

echo ">> Building dms-gitea:local (Debian 13 Gitea server)"
docker build "${PROXY_ARGS[@]}" -t dms-gitea:local -f "$ROOT/infra/gitea/Dockerfile" "$ROOT/infra/gitea"

echo ">> Building dms-act-runner:local (Debian 13 Gitea Actions runner)"
docker build "${PROXY_ARGS[@]}" -t dms-act-runner:local -f "$ROOT/infra/gitea/act-runner.Dockerfile" "$ROOT/infra/gitea"

echo ">> Pulling Mongo, Keycloak, the bootstrap Python, and the dind sidecar (host docker, uses the proxy)"
docker pull mongo:7
docker pull quay.io/keycloak/keycloak:26.2
docker pull python:3.13-slim-trixie
docker pull docker:27-dind

echo ">> Loading everything into minikube"
for img in dms-backend:local dms-frontend:local dms-ocr-worker:local \
           dms-gitea:local dms-act-runner:local \
           mongo:7 quay.io/keycloak/keycloak:26.2 python:3.13-slim-trixie docker:27-dind; do
  echo "   - $img"
  minikube image load "$img"
done

echo ">> Done. What minikube has now:"
minikube image ls | grep -E 'dms-|mongo|keycloak' || true
