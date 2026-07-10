#!/usr/bin/env bash
# Gitea server entrypoint. Renders a config file on first boot (kept on the PVC so
# Gitea's own generated SECRET_KEY / INTERNAL_TOKEN survive restarts), applies any
# pending schema migrations, ensures the admin account exists, then hands off to
# `gitea web`. Actions and the container registry (Packages) are both enabled.
set -euo pipefail

CONF="${GITEA_CUSTOM}/conf/app.ini"
mkdir -p "$(dirname "$CONF")" "${GITEA_WORK_DIR}/data" "${GITEA_WORK_DIR}/log"

if [ ! -f "$CONF" ]; then
  echo ">> Writing initial Gitea config to $CONF"
  cat > "$CONF" <<EOF
APP_NAME = DMS Gitea
RUN_MODE = prod

[server]
PROTOCOL = http
HTTP_PORT = 3000
DOMAIN = ${GITEA_DOMAIN:-localhost}
ROOT_URL = ${GITEA_ROOT_URL:-http://localhost:3000/}
SSH_DOMAIN = ${GITEA_DOMAIN:-localhost}
START_SSH_SERVER = true
SSH_PORT = ${GITEA_SSH_DOMAIN_PORT:-2222}
SSH_LISTEN_PORT = 2222
LFS_START_SERVER = true
OFFLINE_MODE = true

[database]
DB_TYPE = sqlite3
PATH = ${GITEA_WORK_DIR}/data/gitea.db

[security]
INSTALL_LOCK = true

[service]
DISABLE_REGISTRATION = true
REQUIRE_SIGNIN_VIEW = false

[repository]
ENABLE_PUSH_CREATE_USER = true
DEFAULT_PRIVATE = private

[actions]
ENABLED = true

[packages]
ENABLED = true

[log]
LEVEL = Info
EOF
fi

echo ">> Applying database migrations"
gitea migrate --config "$CONF"

if [ -n "${GITEA_ADMIN_USERNAME:-}" ] && [ -n "${GITEA_ADMIN_PASSWORD:-}" ]; then
  if gitea admin user list --config "$CONF" --admin \
       | awk 'NR>1 {print $2}' | grep -qx "${GITEA_ADMIN_USERNAME}"; then
    echo ">> Admin user '${GITEA_ADMIN_USERNAME}' already exists"
  else
    echo ">> Creating admin user '${GITEA_ADMIN_USERNAME}'"
    gitea admin user create --config "$CONF" \
      --username "${GITEA_ADMIN_USERNAME}" \
      --password "${GITEA_ADMIN_PASSWORD}" \
      --email "${GITEA_ADMIN_EMAIL:-admin@dms.local}" \
      --admin --must-change-password=false
  fi
fi

echo ">> Starting Gitea web server"
exec gitea web --config "$CONF"
