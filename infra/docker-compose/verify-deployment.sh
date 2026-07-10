#!/usr/bin/env bash
# End-to-end acceptance test for a running Compose stack. Verifies the
# acceptance criteria of the department feature without any manual Keycloak
# steps:
#
#   1. MongoDB, Keycloak, and the backend are healthy; the bootstrap ran.
#   2. The Keycloak bootstrap is idempotent (re-running it succeeds and
#      verifies the same converged state).
#   3. The managed `department` user-profile attribute exists.
#   4. manager/contributor/viewer carry department=ITDLZ, admin carries none.
#   5. The dms-frontend department mapper is present (access/ID/userinfo).
#   6. ITDLZ exists in the Mongo department registry and the admin API lists it.
#   7. A dms_admin user can create (and clean up) a department; a viewer gets 403.
#   8. A manager login token really carries the department=ITDLZ claim.
#   9. A dms_admin can assign a real department to a real Keycloak user through
#      the backend, the attribute lands in Keycloak, the username stays
#      unchanged, the assignment can be restored to ITDLZ, and a viewer gets
#      403 attempting the same. (Regression: a Keycloak user-profile validation
#      rejection previously surfaced only as "Unexpected server error".)
#
# Tokens are obtained through the same PKCE authorization-code flow the SPA
# uses — Direct Access Grants stay disabled. Requires: docker compose, curl,
# python3. Secrets are read from .env and never printed.
#
# Run from anywhere:  bash infra/docker-compose/verify-deployment.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
REDIRECT_URI="http://localhost:5173/"

if [[ ! -f .env ]]; then
  echo "ERROR: $SCRIPT_DIR/.env is missing. Run generate-env.sh first." >&2
  exit 1
fi

# Read one variable out of .env without exporting or echoing the whole file.
env_value() {
  grep -E "^$1=" .env | tail -n1 | cut -d= -f2-
}
KC_ADMIN_USER="$(env_value KEYCLOAK_ADMIN_USER)"
KC_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)"
DEMO_PASSWORD="$(env_value DMS_DEMO_USER_PASSWORD)"
MONGO_APP_USER="$(env_value MONGO_APP_USER)"
MONGO_APP_PASSWORD="$(env_value MONGO_APP_PASSWORD)"

PASS=0
FAIL=0
# Evaluates a bash expression in this shell; prints PASS/FAIL with the label.
check() {
  local description=$1 expression=$2
  if eval "$expression" >/dev/null 2>&1; then
    echo "PASS  $description"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $description" >&2
    FAIL=$((FAIL + 1))
  fi
}

json_get() { # json_get <python-expression over parsed stdin as j>
  python3 -c "import json,sys; j=json.load(sys.stdin); print($1)"
}

compose_health() {
  docker compose ps --format json "$1" 2>/dev/null | json_get "j['Health'] or j['State']"
}

echo "== 1. Container health =="
check "mongodb is healthy" '[ "$(compose_health mongodb)" = "healthy" ]'
check "keycloak is healthy" '[ "$(compose_health keycloak)" = "healthy" ]'
check "backend is healthy" '[ "$(compose_health backend)" = "healthy" ]'
check "bootstrap completed successfully" \
  '[ "$(docker compose ps -a --format json keycloak-bootstrap | json_get "str(j[\"ExitCode\"])")" = "0" ]'
check "backend /health responds" "curl -sf $BACKEND_URL/health"

echo "== 2. Bootstrap idempotency (second run) =="
check "bootstrap re-run succeeds (idempotent)" \
  'docker compose run --rm keycloak-bootstrap'

echo "== 3. Keycloak state via Admin API =="
MASTER_TOKEN="$(curl -sf "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$KC_ADMIN_USER" --data-urlencode "password=$KC_ADMIN_PASSWORD" \
  | json_get "j['access_token']")" || { echo "ERROR: no Keycloak admin token" >&2; exit 1; }

kc_admin() {
  curl -sf -H "Authorization: Bearer $MASTER_TOKEN" "$KEYCLOAK_URL/admin/realms/dms$1"
}

user_department() {
  kc_admin "/users?username=$1%40dms.local&exact=true" \
    | json_get "(j[0].get('attributes') or {}).get('department',[''])[0]"
}

check "user profile declares managed 'department'" \
  'kc_admin /users/profile | json_get "[a for a in j[\"attributes\"] if a[\"name\"]==\"department\"][0][\"name\"]"'
check "manager has department=ITDLZ" '[ "$(user_department manager)" = "ITDLZ" ]'
check "contributor has department=ITDLZ" '[ "$(user_department contributor)" = "ITDLZ" ]'
check "viewer has department=ITDLZ" '[ "$(user_department viewer)" = "ITDLZ" ]'
check "admin has no department" '[ -z "$(user_department admin)" ]'

frontend_mapper_ok() {
  local cid
  cid="$(kc_admin "/clients?clientId=dms-frontend" | json_get 'j[0]["id"]')" || return 1
  kc_admin "/clients/$cid/protocol-mappers/models" | json_get '
[m for m in j if m["name"]=="department"
 and m["config"]["access.token.claim"]=="true"
 and m["config"]["id.token.claim"]=="true"
 and m["config"]["userinfo.token.claim"]=="true"][0]["name"]'
}
check "department mapper on dms-frontend (access+id+userinfo)" frontend_mapper_ok

echo "== 4. Department registry in MongoDB =="
check "ITDLZ exists in the departments collection" \
  "docker compose exec -T mongodb mongosh --quiet \
     -u '$MONGO_APP_USER' -p '$MONGO_APP_PASSWORD' --authenticationDatabase dms dms \
     --eval 'db.departments.countDocuments({code: \"ITDLZ\", active: true})' | grep -qx 1"

echo "== 5. Backend admin API (PKCE login flow, no direct grants) =="
# Logs a demo user in through the browser flow and prints an access token.
user_token() {
  python3 - "$KEYCLOAK_URL" "$1" "$DEMO_PASSWORD" "$REDIRECT_URI" <<'PYEOF'
import base64, hashlib, html, json, re, secrets, sys, urllib.error, urllib.parse, urllib.request
from http.cookiejar import CookieJar

kc, username, password, redirect_uri = sys.argv[1:5]
verifier = secrets.token_urlsafe(48)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

jar = CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
post_opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect())

auth_url = (f"{kc}/realms/dms/protocol/openid-connect/auth?"
            + urllib.parse.urlencode({
                "client_id": "dms-frontend", "response_type": "code", "scope": "openid",
                "redirect_uri": redirect_uri,
                "code_challenge": challenge, "code_challenge_method": "S256"}))
page = opener.open(auth_url).read().decode()
action = html.unescape(re.search(r'action="([^"]+)"', page).group(1))

# Keycloak marks its auth cookies Secure even over plain http (local dev);
# clear the flag so the cookie jar still sends them with the http POST below.
for cookie in jar:
    cookie.secure = False

form = urllib.parse.urlencode({"username": username, "password": password}).encode()
try:
    resp = post_opener.open(urllib.request.Request(action, data=form))
    location = resp.headers.get("Location", "")
except urllib.error.HTTPError as e:
    location = e.headers.get("Location", "")
code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query).get("code", [None])[0]
if not code:
    sys.exit(f"login flow for {username} did not return an authorization code")

token = json.load(opener.open(f"{kc}/realms/dms/protocol/openid-connect/token",
    data=urllib.parse.urlencode({
        "grant_type": "authorization_code", "client_id": "dms-frontend",
        "code": code, "redirect_uri": redirect_uri, "code_verifier": verifier}).encode()))
print(token["access_token"])
PYEOF
}

ADMIN_TOKEN="$(user_token admin@dms.local)" || { echo "ERROR: admin login failed" >&2; exit 1; }
VIEWER_TOKEN="$(user_token viewer@dms.local)" || { echo "ERROR: viewer login failed" >&2; exit 1; }
MANAGER_TOKEN="$(user_token manager@dms.local)" || { echo "ERROR: manager login failed" >&2; exit 1; }

manager_claim_ok() {
  local payload
  payload="$(cut -d. -f2 <<<"$MANAGER_TOKEN")"
  python3 - "$payload" <<'PYEOF'
import base64, json, sys
p = sys.argv[1]
claims = json.loads(base64.urlsafe_b64decode(p + "=" * (-len(p) % 4)))
assert claims.get("department") == "ITDLZ", claims.get("department")
PYEOF
}
check "manager JWT carries department=ITDLZ" manager_claim_ok

check "admin API lists ITDLZ" \
  "curl -sf -H 'Authorization: Bearer $ADMIN_TOKEN' $BACKEND_URL/api/admin/departments \
   | json_get \"[d for d in j if d['code']=='ITDLZ'][0]['code']\""

TEST_CODE="VERIFY$(date +%s)"
check "dms_admin can create a department" \
  "curl -sf -X POST -H 'Authorization: Bearer $ADMIN_TOKEN' -H 'Content-Type: application/json' \
     -d '{\"code\":\"$TEST_CODE\",\"displayName\":\"Acceptance probe\"}' \
     $BACKEND_URL/api/admin/departments | json_get \"j['code']\" | grep -qx $TEST_CODE"
check "created department can be deleted again (unreferenced)" \
  "curl -sf -X DELETE -H 'Authorization: Bearer $ADMIN_TOKEN' $BACKEND_URL/api/admin/departments/$TEST_CODE"
check "non-admin gets 403 creating a department" \
  "[ \"\$(curl -s -o /dev/null -w '%{http_code}' -X POST \
      -H 'Authorization: Bearer $VIEWER_TOKEN' -H 'Content-Type: application/json' \
      -d '{\"code\":\"NOPE1\"}' $BACKEND_URL/api/admin/departments)\" = 403 ]"

echo "== 6. Live user assignment through the backend =="
# Assign a real temporary department to the real viewer user, confirm it in
# Keycloak, then restore ITDLZ and delete the temporary department. Restore and
# delete run as their own checks even when earlier assertions fail, so a broken
# intermediate state is still cleaned up (and a failed cleanup is itself
# reported instead of silently leaving state behind).
ASSIGN_CODE="ASSIGN$(date +%s)"
VIEWER_ID="$(kc_admin "/users?username=viewer%40dms.local&exact=true" | json_get 'j[0]["id"]')"

check "temporary department can be created" \
  "curl -sf -X POST -H 'Authorization: Bearer $ADMIN_TOKEN' -H 'Content-Type: application/json' \
     -d '{\"code\":\"$ASSIGN_CODE\",\"displayName\":\"Assignment probe\"}' \
     $BACKEND_URL/api/admin/departments | json_get \"j['code']\" | grep -qx $ASSIGN_CODE"
check "admin assigns it to viewer via the backend" \
  "curl -sf -X PUT -H 'Authorization: Bearer $ADMIN_TOKEN' -H 'Content-Type: application/json' \
     -d '{\"department\":\"$ASSIGN_CODE\"}' $BACKEND_URL/api/admin/users/$VIEWER_ID/department"
check "Keycloak now shows the assignment on the viewer" \
  "[ \"\$(user_department viewer)\" = \"$ASSIGN_CODE\" ]"
check "viewer username unchanged by the assignment" \
  'kc_admin "/users?username=viewer%40dms.local&exact=true" | json_get "j[0][\"username\"]" | grep -qx viewer@dms.local'
check "admin restores the viewer to ITDLZ" \
  "curl -sf -X PUT -H 'Authorization: Bearer $ADMIN_TOKEN' -H 'Content-Type: application/json' \
     -d '{\"department\":\"ITDLZ\"}' $BACKEND_URL/api/admin/users/$VIEWER_ID/department"
check "Keycloak shows ITDLZ restored" \
  '[ "$(user_department viewer)" = "ITDLZ" ]'
check "temporary department can be deleted again" \
  "curl -sf -X DELETE -H 'Authorization: Bearer $ADMIN_TOKEN' \
     $BACKEND_URL/api/admin/departments/$ASSIGN_CODE"
check "non-admin gets 403 assigning a department" \
  "[ \"\$(curl -s -o /dev/null -w '%{http_code}' -X PUT \
      -H 'Authorization: Bearer $VIEWER_TOKEN' -H 'Content-Type: application/json' \
      -d '{\"department\":\"ITDLZ\"}' $BACKEND_URL/api/admin/users/$VIEWER_ID/department)\" = 403 ]"

echo ""
echo "Result: $PASS passed, $FAIL failed."
[[ $FAIL -eq 0 ]]
