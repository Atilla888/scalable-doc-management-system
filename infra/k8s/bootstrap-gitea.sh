#!/usr/bin/env bash
# One-time Gitea/CI wiring, run after deploy.sh once the Gitea server pod is Ready.
# It does the three things Gitea can only provide at runtime:
#   1. mints an Actions runner registration token and patches it into dms-secrets,
#      so the gitea-runner pod can register and start;
#   2. creates the image-pull Secret the app Deployments use to pull CI-built images
#      from the in-cluster Gitea registry, and wires it onto those Deployments;
#   3. prints the remaining manual steps (create the repo, push, watch the pipeline).
#
# Run from anywhere:  bash infra/k8s/bootstrap-gitea.sh
set -euo pipefail

NS=dms
CONF=/var/lib/gitea/custom/conf/app.ini

echo ">> Waiting for the Gitea server to be Ready..."
kubectl -n "$NS" rollout status deployment/gitea --timeout=300s

echo ">> Reading Gitea admin credentials from dms-secrets"
GITEA_USER="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.gitea-admin-username}' | base64 -d)"
GITEA_PW="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.gitea-admin-password}' | base64 -d)"

# 1) Runner registration token ------------------------------------------------
echo ">> Generating an Actions runner registration token"
TOKEN="$(kubectl -n "$NS" exec deploy/gitea -- gitea actions generate-runner-token -c "$CONF" | tr -d '\r\n')"
if [[ -z "$TOKEN" ]]; then
  echo "ERROR: could not obtain a runner token from Gitea." >&2
  exit 1
fi
echo ">> Patching dms-secrets with gitea-runner-token"
kubectl -n "$NS" patch secret dms-secrets --type merge \
  -p "{\"stringData\":{\"gitea-runner-token\":\"${TOKEN}\"}}"

# The runner pod is stuck in CreateContainerConfigError until the key exists; nudge it.
kubectl -n "$NS" rollout restart deployment/gitea-runner
echo ">> Runner token installed. Waiting for the runner to come up..."
kubectl -n "$NS" rollout status deployment/gitea-runner --timeout=180s || true

# 2) Image-pull secret for CI-built images ------------------------------------
NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')"
REGISTRY="${NODE_IP}:30300"
echo ">> Creating image-pull secret 'gitea-registry' for ${REGISTRY}"
kubectl -n "$NS" create secret docker-registry gitea-registry \
  --docker-server="${REGISTRY}" \
  --docker-username="${GITEA_USER}" \
  --docker-password="${GITEA_PW}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ">> Attaching the pull secret to the app Deployments"
for dep in backend frontend ocr-worker; do
  kubectl -n "$NS" patch deployment "$dep" --type merge \
    -p '{"spec":{"template":{"spec":{"imagePullSecrets":[{"name":"gitea-registry"}]}}}}'
done

cat <<EOF

>> Gitea CI is wired up.

Registry (in-cluster): ${REGISTRY}
Gitea admin:           ${GITEA_USER} / (see generate-secrets.sh output)

Remaining manual steps (through the SSH tunnel to http://localhost:3000):
  1. Log in to Gitea, create an organization or user 'dms', and a repo 'dms'.
  2. Add this repository as a remote and push:
        git remote add gitea http://localhost:3000/dms/dms.git
        git push gitea main
  3. In the repo: Settings -> Actions -> enable, and confirm the runner
     'dms-k8s-runner' shows as Online under the site/admin Actions page.
  4. The push triggers .gitea/workflows/ci.yml: tests -> build -> push to the
     registry -> rollout. Watch it under the repo's Actions tab.

Note: the kubelet must be allowed to pull over plaintext HTTP from ${REGISTRY}.
Start minikube with:  minikube start --insecure-registry "${NODE_IP}/32"
(or the matching CIDR). See infra/gitea/README.md.
EOF
