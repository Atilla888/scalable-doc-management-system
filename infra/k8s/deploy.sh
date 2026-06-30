#!/usr/bin/env bash
# Bring the whole DMS stack up on minikube, in the right order. Safe to re-run —
# the ConfigMaps are applied idempotently and the manifests are declarative.
#
# Run from anywhere:  bash infra/k8s/deploy.sh
# Before this: minikube up, secrets generated, images loaded (build-and-load.sh).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
NS=dms

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

echo ">> Waiting for those to roll out (up to 5 min)..."
kubectl -n "$NS" rollout status deployment/backend --timeout=300s
kubectl -n "$NS" rollout status deployment/frontend --timeout=300s
kubectl -n "$NS" rollout status deployment/ocr-worker --timeout=300s

echo ""
echo ">> Up and running:"
kubectl -n "$NS" get pods -o wide
echo ""
echo ">> NodePorts to tunnel from your laptop (see infra/k8s/README.md):"
echo "   frontend -> 30573   keycloak -> 30080   backend -> 30081"
echo "   Want host-based routing instead? kubectl apply -f $SCRIPT_DIR/08-ingress.yaml"
