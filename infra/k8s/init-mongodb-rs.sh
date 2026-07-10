#!/usr/bin/env bash
# One-time initiation for the OPTIONAL MongoDB replica set (02-mongodb-replicaset.yaml).
# Run it once, after the three mongodb-N pods are Ready. It is idempotent, re-running
# is safe.
#
# It does, in order:
#   1. initiates the 3-member replica set rs0 (via the localhost exception on
#      mongodb-0, before any user exists);
#   2. creates the root admin user (localhost exception);
#   3. creates the app user + collections + indexes + root folder by running
#      scripts/mongo-init.js authenticated as root;
#   4. points the backend and OCR worker at the replica set and rolls them.
#
# Run from anywhere:  bash infra/k8s/init-mongodb-rs.sh
set -euo pipefail

NS=dms
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

MEMBERS='mongodb-0.mongodb.dms.svc.cluster.local:27017,mongodb-1.mongodb.dms.svc.cluster.local:27017,mongodb-2.mongodb.dms.svc.cluster.local:27017'

echo ">> Waiting for all three MongoDB members to be Ready"
kubectl -n "$NS" rollout status statefulset/mongodb --timeout=300s

echo ">> Reading credentials from dms-secrets"
ROOT_USER="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.mongo-root-username}' | base64 -d)"
ROOT_PW="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.mongo-root-password}' | base64 -d)"
APP_USER="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.mongo-app-username}' | base64 -d)"
APP_PW="$(kubectl -n "$NS" get secret dms-secrets -o jsonpath='{.data.mongo-app-password}' | base64 -d)"

# mongosh on mongodb-0, script from stdin. Extra args (e.g. auth) passed through.
mongosh0() {
  kubectl -n "$NS" exec -i mongodb-0 -- mongosh --quiet "$@"
}

echo ">> Initiating replica set rs0 (idempotent)"
mongosh0 <<JS
var h = db.hello();
if (!h.setName) {
  rs.initiate({
    _id: "rs0",
    members: [
      { _id: 0, host: "mongodb-0.mongodb.dms.svc.cluster.local:27017" },
      { _id: 1, host: "mongodb-1.mongodb.dms.svc.cluster.local:27017" },
      { _id: 2, host: "mongodb-2.mongodb.dms.svc.cluster.local:27017" }
    ]
  });
  print("INITIATED");
} else {
  print("ALREADY A MEMBER OF " + h.setName);
}
JS

echo ">> Waiting for mongodb-0 to become PRIMARY"
for _ in $(seq 1 60); do
  if [ "$(mongosh0 --eval 'db.hello().isWritablePrimary' | tr -d '[:space:]')" = "true" ]; then
    echo "   mongodb-0 is PRIMARY"
    break
  fi
  sleep 3
done

echo ">> Creating the root admin user (idempotent, via localhost exception)"
mongosh0 <<JS
try {
  db.getSiblingDB("admin").createUser({
    user: "${ROOT_USER}",
    pwd: "${ROOT_PW}",
    roles: [{ role: "root", db: "admin" }]
  });
  print("ROOT_CREATED");
} catch (e) {
  print("ROOT_SKIP: " + (e.codeName || e.message));
}
JS

echo ">> Creating the app user, collections, indexes, and root folder (as root)"
{
  printf 'var MONGO_APP_USER=%s;\n' "\"${APP_USER}\""
  printf 'var MONGO_APP_PASSWORD=%s;\n' "\"${APP_PW}\""
  cat "$ROOT/scripts/mongo-init.js"
} | mongosh0 -u "$ROOT_USER" -p "$ROOT_PW" --authenticationDatabase admin

echo ">> Pointing the backend and OCR worker at the replica set"
RS_URI="mongodb://\$(MONGO_APP_USER):\$(MONGO_APP_PASSWORD)@${MEMBERS}/dms?replicaSet=rs0&authSource=dms"
kubectl -n "$NS" set env deployment/backend "SPRING_DATA_MONGODB_URI=${RS_URI}"
kubectl -n "$NS" set env deployment/ocr-worker "MONGODB_URI=${RS_URI}"

echo ">> Waiting for the app to roll to the replica-set connection"
kubectl -n "$NS" rollout status deployment/backend --timeout=240s
kubectl -n "$NS" rollout status deployment/ocr-worker --timeout=180s

cat <<EOF

>> MongoDB replica set rs0 is up with automatic failover.

Verify:
  kubectl -n $NS exec -it mongodb-0 -- mongosh -u $ROOT_USER -p '<root pw>' \\
    --authenticationDatabase admin --eval 'rs.status().members.map(m => m.name + " => " + m.stateStr)'

Test failover (delete the primary; a new one is elected in seconds and the API keeps serving):
  kubectl -n $NS delete pod mongodb-0
  kubectl -n $NS exec -it mongodb-1 -- mongosh -u $ROOT_USER -p '<root pw>' \\
    --authenticationDatabase admin --eval 'rs.status().members.map(m => m.name + " => " + m.stateStr)'
EOF
