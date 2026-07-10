#!/usr/bin/env python3
"""Idempotent Keycloak bootstrap for the dms realm.

Runs after Keycloak has imported the realm and repairs everything the
declarative import cannot express reliably:

  1. Declares a managed `department` attribute in the realm User Profile
     (Keycloak >= 24 silently drops unmanaged user attributes on import).
  2. Ensures manager/contributor/viewer demo users carry department=ITDLZ
     and that the admin demo user carries no department (dms_admin is global).
  3. Ensures the dms-frontend client maps `department` into the access token,
     ID token, and userinfo.
  4. Grants the dms-admin-api service account exactly the realm-management
     roles the backend needs (least privilege, includes manage-users for
     department assignment).
  5. Verifies the final state and exits non-zero with a clear message if
     anything is still missing.

Credentials come from the environment (Compose .env or a Kubernetes Secret);
they are never printed. Safe to run any number of times. Standard library
only, so it runs on the official Debian 13 (trixie) Python image
(python:3.13-slim-trixie) in both environments without extra packages.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

KEYCLOAK_URL = os.environ.get("KEYCLOAK_URL", "http://keycloak:8080").rstrip("/")
REALM = os.environ.get("KEYCLOAK_REALM", "dms")
ADMIN_USER = os.environ.get("KEYCLOAK_ADMIN_USER")
ADMIN_PASSWORD = os.environ.get("KEYCLOAK_ADMIN_PASSWORD")
READY_TIMEOUT_SECONDS = int(os.environ.get("BOOTSTRAP_READY_TIMEOUT", "300"))

DEPARTMENT_CODE = os.environ.get("DMS_DEFAULT_DEPARTMENT", "ITDLZ")
# Demo usernames equal their email addresses: the realm enables
# registrationEmailAsUsername, and Keycloak force-syncs username to email on
# every user update in that mode, so a differing username would silently be
# renamed by the first admin-API write.
DEPARTMENT_USERS = ("manager@dms.local", "contributor@dms.local", "viewer@dms.local")
NO_DEPARTMENT_USERS = ("admin@dms.local",)
FRONTEND_CLIENT_ID = "dms-frontend"
ADMIN_API_CLIENT_ID = "dms-admin-api"
# Least privilege: what the backend directory/department features actually use.
SERVICE_ACCOUNT_ROLES = ("query-users", "view-users", "view-realm", "manage-users")

MAPPER_CONFIG = {
    "user.attribute": "department",
    "claim.name": "department",
    "jsonType.label": "String",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "userinfo.token.claim": "true",
}


def fail(message):
    print(f"BOOTSTRAP FAILED: {message}", file=sys.stderr)
    sys.exit(1)


def request(method, url, token=None, body=None, form=None):
    """HTTP request returning (status, parsed-JSON-or-None). Raises on network errors."""
    headers = {}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw.strip() else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            payload = json.loads(raw) if raw.strip() else None
        except ValueError:
            payload = raw.decode(errors="replace")
        return e.code, payload


def admin(method, path, token, body=None, expect=(200, 201, 204)):
    """Call the Keycloak Admin API and fail loudly on unexpected status."""
    status, payload = request(method, f"{KEYCLOAK_URL}/admin/realms/{REALM}{path}", token=token, body=body)
    if status not in expect:
        fail(f"{method} {path} returned HTTP {status}: {json.dumps(payload)[:500]}")
    return payload


def wait_for_keycloak():
    deadline = time.monotonic() + READY_TIMEOUT_SECONDS
    last_error = "no attempt made"
    while time.monotonic() < deadline:
        try:
            status, _ = request("GET", f"{KEYCLOAK_URL}/realms/master")
            if status == 200:
                print(f"Keycloak is ready at {KEYCLOAK_URL}")
                return
            last_error = f"HTTP {status}"
        except (urllib.error.URLError, OSError) as e:
            last_error = str(e)
        time.sleep(3)
    fail(f"Keycloak did not become ready within {READY_TIMEOUT_SECONDS}s (last error: {last_error})")


def get_admin_token():
    status, payload = request(
        "POST",
        f"{KEYCLOAK_URL}/realms/master/protocol/openid-connect/token",
        form={
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": ADMIN_USER,
            "password": ADMIN_PASSWORD,
        },
    )
    if status != 200 or not payload or "access_token" not in payload:
        fail(f"could not obtain an admin token (HTTP {status}); check the Keycloak admin credentials")
    return payload["access_token"]


def ensure_realm_exists(token):
    status, _ = request("GET", f"{KEYCLOAK_URL}/admin/realms/{REALM}", token=token)
    if status != 200:
        fail(
            f"realm '{REALM}' does not exist (HTTP {status}); the realm import has not run — "
            "check the Keycloak logs for import errors"
        )


def ensure_user_profile(token):
    """Declare `department` as a managed, admin-editable, single-valued attribute."""
    profile = admin("GET", "/users/profile", token)
    attributes = profile.setdefault("attributes", [])
    desired = {
        "displayName": "Department",
        "multivalued": False,
        "permissions": {"view": ["admin", "user"], "edit": ["admin"]},
    }
    existing = next((a for a in attributes if a.get("name") == "department"), None)
    if existing is None:
        attributes.append({"name": "department", **desired})
        changed = True
    else:
        changed = any(existing.get(k) != v for k, v in desired.items())
        existing.update(desired)
    if changed:
        admin("PUT", "/users/profile", token, body=profile)
        print("User profile: declared managed attribute 'department'")
    else:
        print("User profile: managed attribute 'department' already declared")


def find_user(token, username):
    users = admin("GET", f"/users?username={urllib.parse.quote(username)}&exact=true", token)
    if not users:
        fail(f"user '{username}' not found in realm '{REALM}'; the realm import is incomplete")
    return users[0]


def ensure_user_department(token, username, department):
    """Set (or, with department=None, remove) the department attribute of one user."""
    user = find_user(token, username)
    attributes = user.get("attributes") or {}
    current = attributes.get("department")
    if department is None:
        if not current:
            print(f"User {username}: correctly has no department")
            return
        attributes.pop("department", None)
        action = "removed department"
    else:
        if current == [department]:
            print(f"User {username}: department already '{department}'")
            return
        attributes["department"] = [department]
        action = f"set department to '{department}'"
    user["attributes"] = attributes
    admin("PUT", f"/users/{user['id']}", token, body=user)
    print(f"User {username}: {action}")


def get_client(token, client_id):
    clients = admin("GET", f"/clients?clientId={urllib.parse.quote(client_id)}", token)
    if not clients:
        fail(f"client '{client_id}' not found in realm '{REALM}'; the realm import is incomplete")
    return clients[0]


def ensure_department_mapper(token):
    client = get_client(token, FRONTEND_CLIENT_ID)
    mappers = admin("GET", f"/clients/{client['id']}/protocol-mappers/models", token)
    mapper = next((m for m in mappers if m.get("name") == "department"), None)
    desired = {
        "name": "department",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-attribute-mapper",
        "config": MAPPER_CONFIG,
    }
    if mapper is None:
        admin("POST", f"/clients/{client['id']}/protocol-mappers/models", token, body=desired)
        print("Mapper: created 'department' protocol mapper on dms-frontend")
    elif any(mapper.get("config", {}).get(k) != v for k, v in MAPPER_CONFIG.items()):
        mapper["config"] = {**mapper.get("config", {}), **MAPPER_CONFIG}
        admin("PUT", f"/clients/{client['id']}/protocol-mappers/models/{mapper['id']}", token, body=mapper)
        print("Mapper: updated 'department' protocol mapper config")
    else:
        print("Mapper: 'department' protocol mapper already correct")


def ensure_service_account_roles(token):
    api_client = get_client(token, ADMIN_API_CLIENT_ID)
    realm_mgmt = get_client(token, "realm-management")
    service_user = admin("GET", f"/clients/{api_client['id']}/service-account-user", token)
    assigned = admin(
        "GET", f"/users/{service_user['id']}/role-mappings/clients/{realm_mgmt['id']}", token
    ) or []
    assigned_names = {r["name"] for r in assigned}
    missing = [name for name in SERVICE_ACCOUNT_ROLES if name not in assigned_names]
    if not missing:
        print(f"Service account: realm-management roles already assigned ({', '.join(SERVICE_ACCOUNT_ROLES)})")
        return
    roles = [
        admin("GET", f"/clients/{realm_mgmt['id']}/roles/{urllib.parse.quote(name)}", token)
        for name in missing
    ]
    admin("POST", f"/users/{service_user['id']}/role-mappings/clients/{realm_mgmt['id']}", token, body=roles)
    print(f"Service account: assigned realm-management roles: {', '.join(missing)}")


def verify(token):
    """Independently re-read everything; collect problems instead of stopping at the first."""
    problems = []

    profile = admin("GET", "/users/profile", token)
    attr = next((a for a in profile.get("attributes", []) if a.get("name") == "department"), None)
    if attr is None:
        problems.append("user profile has no 'department' attribute")
    else:
        perms = attr.get("permissions") or {}
        if "admin" not in (perms.get("edit") or []):
            problems.append("user profile attribute 'department' is not admin-editable")
        if "user" not in (perms.get("view") or []):
            problems.append("user profile attribute 'department' is not visible to users")
        if attr.get("multivalued"):
            problems.append("user profile attribute 'department' must be single-valued")

    for username in DEPARTMENT_USERS:
        user = find_user(token, username)
        if (user.get("attributes") or {}).get("department") != [DEPARTMENT_CODE]:
            problems.append(f"user '{username}' does not have department={DEPARTMENT_CODE}")
    for username in NO_DEPARTMENT_USERS:
        user = find_user(token, username)
        if (user.get("attributes") or {}).get("department"):
            problems.append(f"user '{username}' must not have a department")

    client = get_client(token, FRONTEND_CLIENT_ID)
    mappers = admin("GET", f"/clients/{client['id']}/protocol-mappers/models", token)
    mapper = next((m for m in mappers if m.get("name") == "department"), None)
    if mapper is None:
        problems.append("dms-frontend has no 'department' protocol mapper")
    else:
        for key, value in MAPPER_CONFIG.items():
            if mapper.get("config", {}).get(key) != value:
                problems.append(f"'department' mapper config {key} != {value}")

    api_client = get_client(token, ADMIN_API_CLIENT_ID)
    realm_mgmt = get_client(token, "realm-management")
    service_user = admin("GET", f"/clients/{api_client['id']}/service-account-user", token)
    assigned = admin(
        "GET", f"/users/{service_user['id']}/role-mappings/clients/{realm_mgmt['id']}", token
    ) or []
    assigned_names = {r["name"] for r in assigned}
    for name in SERVICE_ACCOUNT_ROLES:
        if name not in assigned_names:
            problems.append(f"service account '{ADMIN_API_CLIENT_ID}' is missing realm-management role '{name}'")

    if problems:
        fail("verification found problems:\n  - " + "\n  - ".join(problems))
    print("Verification passed: user profile, user departments, token mapper, and service-account roles are all in place.")


def main():
    if not ADMIN_USER or not ADMIN_PASSWORD:
        fail("KEYCLOAK_ADMIN_USER and KEYCLOAK_ADMIN_PASSWORD must be set (via env or Kubernetes Secret)")
    wait_for_keycloak()
    token = get_admin_token()
    ensure_realm_exists(token)
    ensure_user_profile(token)
    for username in DEPARTMENT_USERS:
        ensure_user_department(token, username, DEPARTMENT_CODE)
    for username in NO_DEPARTMENT_USERS:
        ensure_user_department(token, username, None)
    ensure_department_mapper(token)
    ensure_service_account_roles(token)
    # Fresh token for verification so a long apply phase can't leave us expired.
    verify(get_admin_token())
    print("Keycloak bootstrap completed successfully.")


if __name__ == "__main__":
    main()
