#!/usr/bin/env bash
# Registers this act_runner with Gitea on first boot (the .runner state file is kept
# on the /data PVC, so later restarts skip registration) and then runs the daemon.
# Labels use the ":host" executor so workflow steps run directly in this container,
# which already has the full toolchain; image builds go to the dind sidecar via
# DOCKER_HOST.
set -euo pipefail

: "${GITEA_INSTANCE_URL:?GITEA_INSTANCE_URL is required}"
RUNNER_NAME="${RUNNER_NAME:-dms-k8s-runner}"
RUNNER_LABELS="${RUNNER_LABELS:-dms-ci:host}"

# Wait for the dind sidecar so the first build doesn't race Docker startup.
echo ">> Waiting for the Docker daemon (dind sidecar) ..."
for _ in $(seq 1 60); do
  if docker info >/dev/null 2>&1; then
    echo ">> Docker is up"
    break
  fi
  sleep 2
done

if [ ! -f /data/.runner ]; then
  : "${GITEA_RUNNER_REGISTRATION_TOKEN:?GITEA_RUNNER_REGISTRATION_TOKEN is required for first registration}"
  echo ">> Registering runner '${RUNNER_NAME}' with ${GITEA_INSTANCE_URL}"
  act_runner register --no-interactive \
    --instance "${GITEA_INSTANCE_URL}" \
    --token "${GITEA_RUNNER_REGISTRATION_TOKEN}" \
    --name "${RUNNER_NAME}" \
    --labels "${RUNNER_LABELS}"
else
  echo ">> Runner already registered (found /data/.runner)"
fi

echo ">> Starting act_runner daemon"
exec act_runner daemon
