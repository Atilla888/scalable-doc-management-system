#!/usr/bin/env bash
# Bring the whole DMS stack up on minikube, in the right order. Safe to re-run,
# the ConfigMaps are applied idempotently and the manifests are declarative.
#
# Run from anywhere:  bash infra/k8s/deploy.sh
# Before this: minikube up, secrets generated, images loaded (build-and-load.sh).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
NS=dms

# Preflight: fail fast with a clear message if the host isn't ready, rather than
# half-applying manifests. See docs/test-environment-setup.md.
echo ">> Preflight checks"
for cmd in docker kubectl minikube; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: '$cmd' is not installed or not on PATH." >&2
    echo "       Set up the host with docs/test-environment-setup.md." >&2
    exit 1
  fi
done
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: the Docker daemon is not reachable. Start Docker and add your user to the" >&2
  echo "       'docker' group (see docs/test-environment-setup.md)." >&2
  exit 1
fi
if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "ERROR: no reachable Kubernetes cluster. Start one first, e.g.:" >&2
  echo "       minikube start --driver=docker --insecure-registry \"192.168.49.0/24\"" >&2
  exit 1
fi
echo "   docker, kubectl, minikube present; cluster reachable."

echo ">> Namespace + secrets"
kubectl apply -f "$SCRIPT_DIR/00-namespace.yaml"

if [[ ! -f "$SCRIPT_DIR/01-secrets.yaml" ]]; then
  echo "ERROR: infra/k8s/01-secrets.yaml is missing."
  echo "Generate it first (random values, git-ignored):"
  echo "  bash infra/k8s/generate-secrets.sh"
  exit 1
fi
kubectl apply -f "$SCRIPT_DIR/01-secrets.yaml"

# Build the ConfigMaps from the source files so there's only ever one copy to edit.
echo ">> ConfigMap: mongo-init (from scripts/mongo-init.js)"
kubectl -n "$NS" create configmap mongo-init \
  --from-file=mongo-init.js="$ROOT/scripts/mongo-init.js" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ">> ConfigMap: keycloak-realm (from infra/keycloak/dms-realm.json)"
kubectl -n "$NS" create configmap keycloak-realm \
  --from-file=dms-realm.json="$ROOT/infra/keycloak/dms-realm.json" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ">> MongoDB + Keycloak first (everything else depends on them)"
kubectl apply -f "$SCRIPT_DIR/02-mongodb.yaml"
kubectl apply -f "$SCRIPT_DIR/03-keycloak.yaml"

echo ">> Waiting for them to be ready (up to 5 min)..."
kubectl -n "$NS" rollout status statefulset/mongodb --timeout=300s
kubectl -n "$NS" rollout status deployment/keycloak --timeout=300s

echo ">> Backend, frontend, OCR worker"
kubectl apply -f "$SCRIPT_DIR/04-backend.yaml"
kubectl apply -f "$SCRIPT_DIR/05-frontend.yaml"
kubectl apply -f "$SCRIPT_DIR/06-ocr-worker.yaml"

echo ">> Gitea (repository, Actions CI/CD, OCI registry) + Actions runner"
kubectl apply -f "$SCRIPT_DIR/07-gitea.yaml"

echo ">> PodDisruptionBudgets (keep the stateless tier available during disruptions)"
kubectl apply -f "$SCRIPT_DIR/09-pdb.yaml"

echo ">> Waiting for those to roll out (up to 5 min)..."
kubectl -n "$NS" rollout status deployment/backend --timeout=300s
kubectl -n "$NS" rollout status deployment/frontend --timeout=300s
kubectl -n "$NS" rollout status deployment/ocr-worker --timeout=300s
# Gitea server should come up on its own; the runner waits for its token (bootstrap).
kubectl -n "$NS" rollout status deployment/gitea --timeout=300s

echo ""
echo ">> Up and running:"
kubectl -n "$NS" get pods -o wide
echo ""
echo ">> One-time Gitea/CI wiring (runner token + registry pull secret):"
echo "   bash $SCRIPT_DIR/bootstrap-gitea.sh"
echo ""
echo ">> NodePorts to tunnel from your laptop (see infra/k8s/README.md):"
echo "   frontend -> 30573   keycloak -> 30080   backend -> 30081   gitea -> 30300"
echo "   Want host-based routing instead? kubectl apply -f $SCRIPT_DIR/08-ingress.yaml"
