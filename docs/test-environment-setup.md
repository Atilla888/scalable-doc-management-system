# Debian 13 test environment — first-time setup guide

This guide takes a **fresh Debian 13 (trixie) machine** from zero to a fully running,
verified DMS: first the Docker Compose development stack, then the Kubernetes
production-style deployment on minikube, and finally the automated test suites. No
Java, Node.js, or Python toolchain is installed on the host — everything runs in
containers, and every self-built image is based on Debian 13, matching the course
requirement for the container base.

It assumes a plain VM (or physical machine) with:

| Resource | Compose stack only | Compose + Kubernetes + Gitea CI |
| --- | --- | --- |
| vCPUs | 2 | 4 |
| RAM | 4 GB | 8 GB |
| Disk | 20 GB free | 40 GB free |
| Access | a sudo-capable user, internet access | same |

Two machines appear in this guide, and every command block says which one it runs on:

- **On the Debian VM** — the machine running the DMS. Type these into the VM's
  console window, or into an SSH session once section 3 is set up.
- **On the host computer** — your own laptop/desktop, where the browser runs and
  the SSH tunnels are opened.

All VM commands are run as your normal user; only package installation uses `sudo`.

## 1. Base system

**On the Debian VM:**

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y git curl ca-certificates openssl python3 openssh-server
```

`python3` and `openssl` ship with Debian 13 by default; they are listed because the
secret generators (`openssl`) and the deployment verifier (`python3`) need them.
`openssh-server` is what lets your host computer reach the VM in section 3 — it is
often already installed and running, and reinstalling it is harmless.

## 2. Docker Engine (with Compose v2)

Debian's own `docker.io` package works, but the project is tested against Docker CE
from Docker's repository, which also ships the required Compose v2 plugin.

**On the Debian VM:**

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/debian trixie stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Let your user run Docker without sudo, then **log out and back in** (or `newgrp docker`):

```bash
sudo usermod -aG docker "$USER"
```

Verify before continuing — both must work without sudo:

```bash
docker run --rm hello-world
docker compose version        # needs Compose v2.20+
```

## 3. Connecting to the Debian VM over SSH

The application is used from a **browser on your host computer**, and that requires
SSH tunnels into the VM (section 5.4 explains why). Set up plain SSH access first.
How you reach the VM depends on its network mode — in VirtualBox, check it under
**VM Settings → Network → Adapter 1**.

### Case A — the VM has its own IP address (Bridged Adapter or Host-only Adapter)

**On the Debian VM**, find the address:

```bash
hostname -I
```

**On the host computer**, connect with your VM username and that address (SSH uses
its normal port 22 unless you reconfigured the VM's SSH server):

```bash
ssh <user>@<VM_IP>
```

### Case B — VirtualBox NAT (the default adapter type)

With NAT, the VM has no address your host can reach directly — `hostname -I` shows
something like `10.0.2.15`, which exists only inside VirtualBox. Instead, forward a
host port to the VM's SSH server:

1. In VirtualBox: **VM Settings → Network → Adapter 1 → NAT → Advanced →
   Port Forwarding**.
2. Add a rule:

   | Field | Value |
   | --- | --- |
   | Name | SSH |
   | Protocol | TCP |
   | Host IP | 127.0.0.1 |
   | Host Port | 2222 |
   | Guest IP | *(leave blank)* |
   | Guest Port | 22 |

3. **On the host computer**, connect to the forwarded port:

   ```bash
   ssh -p 2222 <user>@127.0.0.1
   ```

**`2222` is only an example.** Any free host port works — use whatever you entered
as *Host Port*, both here and in every later command shown with `-p 2222`.

Once `ssh` gives you a shell prompt inside the VM, you can run all remaining
"On the Debian VM" steps through that session instead of the console window.

## 4. Get the repository

**On the Debian VM:**

```bash
git clone <YOUR_GITEA_OR_UPSTREAM_URL> dms && cd dms
```

Everything below is run from the repository root unless stated otherwise.

## 5. Development stack (Docker Compose)

### 5.1 Generate local secrets

The stack has **no fallback passwords** — it refuses to start until every secret is
set. Generate a `.env` with strong random values (git-ignored, never committed).

**On the Debian VM:**

```bash
cd infra/docker-compose
bash generate-env.sh
```

**Write down the two lines it prints** — the demo-user login password and the
Keycloak admin password. They are stored nowhere else except the `.env` file itself.

### 5.2 Start the stack

**On the Debian VM:**

```bash
docker compose up -d --build --wait
docker compose ps
```

The first build takes several minutes (Maven and npm downloads). `--wait` returns
only when everything is healthy. What comes up, in dependency order:

1. **mongodb** — seeded by `scripts/mongo-init.js` (collections, indexes, root
   folder, default `ITDLZ` department) on first boot.
2. **keycloak** — imports the `dms` realm (roles, clients, demo users).
3. **keycloak-bootstrap** — one-shot job on `python:3.13-slim-trixie`; declares the
   managed `department` user-profile attribute (Keycloak ≥ 24 drops undeclared
   attributes), enforces `department=ITDLZ` on the demo users, verifies the token
   mapper and service-account roles, then exits 0. Idempotent.
4. **backend** — starts only after the bootstrap succeeded and MongoDB is healthy.
5. **frontend**, **ocr-worker**.

### 5.3 Verify the deployment

Run the automated acceptance check — 26 checks covering container health, bootstrap
idempotency, the Keycloak user profile, demo-user departments, the JWT `department`
claim, the department registry, live department assignment to a real user, and
admin-vs-viewer authorization.

**On the Debian VM:**

```bash
bash verify-deployment.sh
```

Expected final line: `Result: 26 passed, 0 failed.`

### 5.4 Open the SSH tunnels and use the application

The Keycloak realm only allows `http://localhost:…` redirect URLs, so the browser
must see the application **as localhost** — browsing to the VM's IP address will
fail at the login redirect. SSH tunnels make the VM's ports appear as local ports
on your host.

**On the host computer** (not inside the VM), matching your case from section 3:

Case A — VM with its own IP:

```bash
ssh \
  -L 5173:localhost:5173 \
  -L 8080:localhost:8080 \
  -L 8081:localhost:8081 \
  <user>@<VM_IP>
```

Case B — VirtualBox NAT (replace `2222` with your configured *Host Port*):

```bash
ssh -p 2222 \
  -L 5173:localhost:5173 \
  -L 8080:localhost:8080 \
  -L 8081:localhost:8081 \
  <user>@127.0.0.1
```

The command logs you into the VM and then **appears to do nothing more — that is
normal**. The tunnels exist only while this SSH session is open, so leave that
terminal running the whole time you use the application.

Now, **in the browser on the host computer**:

| Service | URL |
| --- | --- |
| Frontend | http://localhost:5173 |
| Backend REST + CMIS + Swagger UI | http://localhost:8081 (`/swagger-ui.html`) |
| Keycloak console | http://localhost:8080 |

Demo accounts (password = the `Demo login` value printed by `generate-env.sh` in
step 5.1):

- `admin@dms.local` — `dms_admin`, no department, sees the `/admin` console
- `manager@dms.local` — `dms_department_manager`, department `ITDLZ`
- `contributor@dms.local` — `dms_contributor`, department `ITDLZ`
- `viewer@dms.local` — `dms_viewer`, read-only

Suggested first tour: sign in as **admin** → `/admin` shows live Keycloak users,
roles, health, metrics, and the department registry (create a department, assign it
to a user). Sign in as **contributor** → upload a PDF (it receives an EAP file
number, OCR runs in the background) → find it via full-text search. Sign in as
**viewer** → confirm uploads and admin pages are refused.

## 6. Automated tests (all containerized)

**Backend** (unit + integration; integration tests need the Compose MongoDB from
section 5, reachable on 27018 — read the app password from `.env` yourself, don't
echo it into your shell history on shared machines).

**On the Debian VM:**

```bash
cd infra/docker-compose
MONGO_APP_PASSWORD="$(grep -E '^MONGO_APP_PASSWORD=' .env | cut -d= -f2-)"
docker run --rm --network host \
  -e DMS_TEST_MONGODB_URI="mongodb://dms_app:${MONGO_APP_PASSWORD}@localhost:27018/dms?authSource=dms" \
  -v "$(git rev-parse --show-toplevel)":/repo -w /repo/backend \
  maven:3.9-eclipse-temurin-21 mvn -B -ntp test
```

Expected: **162 tests, 0 failures** (8 skipped — a Testcontainers duplicate of the
Mongo-init tests that cannot reach Docker from inside the Maven container). Without
the `DMS_TEST_MONGODB_URI` override the 54 database-backed tests skip themselves and
only the 108 pure unit tests run.

**Frontend** (Vitest + production build) — **on the Debian VM:**

```bash
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$(git rev-parse --show-toplevel)/frontend":/app -w /app \
  node:lts-trixie-slim bash -c "npm ci && npm test && npm run build"
```

**OCR worker** (pytest) — **on the Debian VM:**

```bash
docker run --rm -v "$(git rev-parse --show-toplevel)/ocr-worker":/app -w /app \
  python:3.13-slim-trixie bash -c \
  "pip install -q -r requirements.txt -r requirements-dev.txt && pytest -q"
```

## 7. Production-style stack (Kubernetes on minikube)

### 7.1 Install kubectl and minikube

**On the Debian VM:**

```bash
# kubectl from the official Kubernetes repository
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.32/deb/Release.key \
  | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes.gpg] \
  https://pkgs.k8s.io/core:/stable:/v1.32/deb/ /" \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list > /dev/null
sudo apt update && sudo apt install -y kubectl

# minikube (Docker driver — reuses the Docker Engine from section 2)
curl -fsSLo /tmp/minikube.deb \
  https://storage.googleapis.com/minikube/releases/latest/minikube_latest_amd64.deb
sudo apt install -y /tmp/minikube.deb && rm /tmp/minikube.deb

minikube start --driver=docker
minikube status    # host, kubelet, apiserver: Running
```

If your network uses an HTTP proxy, export `NO_PROXY=192.168.49.0/24,127.0.0.1`
(minikube's subnet) in `~/.bashrc`, or `kubectl` will hang.

### 7.2 Deploy

Three scripts, run from the repository root.

**On the Debian VM:**

```bash
bash infra/k8s/generate-secrets.sh   # once per cluster; WRITE DOWN what it prints
bash infra/k8s/build-and-load.sh     # builds the Debian 13 images, loads them into minikube
bash infra/k8s/deploy.sh             # applies manifests in order and waits
```

`deploy.sh` deploys MongoDB and Keycloak first, then runs the **keycloak-bootstrap
Job** (same script as Compose, republished as a ConfigMap) and aborts with the Job's
logs if it fails — the backend is only applied after the realm is verified. It ends
with every pod `Running`; the `gitea-runner` pod staying in
`CreateContainerConfigError` is **expected** until step 7.4.

### 7.3 Reach the cluster from the host computer

The minikube node has its own address **inside the VM**. Get it first —
**on the Debian VM:**

```bash
minikube ip     # e.g. 192.168.49.2 — this is <MINIKUBE_IP> below
```

Then tunnel the NodePorts so the `localhost` URLs and the Keycloak token issuer
line up (same reasoning as section 5.4 — do not browse to the VM or minikube IP
directly). If a Compose tunnel from section 5.4 is still open, close it first;
both use the same local ports.

**On the host computer**, matching your case from section 3:

Case A — VM with its own IP:

```bash
ssh \
  -L 5173:<MINIKUBE_IP>:30573 \
  -L 8080:<MINIKUBE_IP>:30080 \
  -L 8081:<MINIKUBE_IP>:30081 \
  <user>@<VM_IP>
```

Case B — VirtualBox NAT (replace `2222` with your configured *Host Port* from
section 3):

```bash
ssh -p 2222 \
  -L 5173:<MINIKUBE_IP>:30573 \
  -L 8080:<MINIKUBE_IP>:30080 \
  -L 8081:<MINIKUBE_IP>:30081 \
  <user>@127.0.0.1
```

As before: keep the SSH session open, and use `http://localhost:5173` in the
browser on the host — same demo users as section 5.4, with the password from
`generate-secrets.sh` (`demo-user-password`).

Useful checks and demos — **on the Debian VM:**

```bash
kubectl -n dms get pods                       # all Running / READY
kubectl -n dms logs job/keycloak-bootstrap    # bootstrap verification output
kubectl -n dms delete pod -l app=backend      # self-healing: replicas keep serving
kubectl -n dms scale deploy/ocr-worker --replicas=3
```

### 7.4 Optional: in-cluster Gitea (repository, CI/CD, OCI registry)

**On the Debian VM:**

```bash
bash infra/k8s/bootstrap-gitea.sh
```

This mints the Actions runner token and registry pull secret; afterwards the runner
pod recovers on its own. Push the repository into Gitea (NodePort `30300`) and the
CI pipeline in `.gitea/workflows/ci.yml` tests every component, builds the Debian 13
images, pushes them to the OCI registry, and rolls the Deployments. See
`infra/k8s/README.md` and `infra/gitea/README.md` for details.

## 8. Resetting

**On the Debian VM:**

| Goal | Command |
| --- | --- |
| Fresh Compose stack (wipes data, reruns realm import + Mongo init + bootstrap) | `cd infra/docker-compose && docker compose down -v --remove-orphans && docker compose up -d --build --wait` |
| New Compose secrets | `bash generate-env.sh --force`, then the fresh-stack command above |
| Stop everything, keep data | `docker compose stop` / `minikube stop` |
| Remove the cluster entirely | `minikube delete` (then regenerate secrets on the next cluster: `bash infra/k8s/generate-secrets.sh --force`) |

## 9. Troubleshooting

- **`ssh: connect to host … refused/timed out`** — with NAT, the port-forwarding
  rule is missing or your `-p` value doesn't match the configured *Host Port*
  (section 3, case B); with bridged/host-only, re-check the address with
  `hostname -I`. Also confirm the SSH server is running in the VM:
  `systemctl status ssh`.
- **The login page loads but the redirect fails** — you browsed to the VM's or
  minikube's IP instead of `http://localhost:5173`, or the tunnel terminal was
  closed. Only `localhost` is an allowed redirect origin (sections 5.4 / 7.3).
- **`docker compose up` refuses to start with a missing-variable error** — your
  `.env` predates a newer required key. `bash generate-env.sh --force`, then a fresh
  start with `down -v` (the old volumes were initialized with the old passwords).
- **A local port is already in use when opening the tunnel** — another service or
  an old tunnel holds 5173/8080/8081 **on the host computer**:
  `ss -ltnp | grep <port>`. On the VM side, MongoDB's host port can be moved with
  `MONGO_HOST_PORT` in `.env`.
- **Demo users can't log in / department missing from the token** — read
  `docker compose logs keycloak-bootstrap` (Compose) or
  `kubectl -n dms logs job/keycloak-bootstrap` (k8s); the bootstrap names the exact
  failed check. Re-running it is always safe:
  `docker compose run --rm keycloak-bootstrap`.
- **k8s pod in `ImagePullBackOff`** — the image never reached minikube. Re-run
  `bash infra/k8s/build-and-load.sh` and check `minikube image ls | grep dms-`.
- **`kubectl` hangs** — proxy environment lost; `source ~/.bashrc` (see 7.1).
- **Backend not READY in k8s** — usually waiting for MongoDB:
  `kubectl -n dms logs mongodb-0 | grep -i "DMS database initialized"`.
- **`./mvnw` fails on a fresh clone** — the wrapper's `.mvn` directory is
  git-ignored; use the containerized Maven command from section 6 (or a system
  `mvn`, as CI does).

## 10. Course-requirement checklist → how to verify it here

| Requirement | Where it lives | Proof on this machine |
| --- | --- | --- |
| Central document archive with upload, folders, EAP file numbers | Spring Boot backend, MongoDB/GridFS | Upload in the UI; number appears on the document page |
| Full-text search incl. OCR text | `ocr-worker`, `/api/search` | Upload a scanned PDF, search its content after OCR completes |
| Role-based access control via central IAM | Keycloak realm + backend RBAC resolver | `verify-deployment.sh` (admin 201 vs viewer 403); backend test suite (162 tests) |
| Department model on users, tokens, and ACLs | Keycloak `department` attribute + JWT claim + Mongo registry | `/admin` department UI; JWT claim check in `verify-deployment.sh` |
| Standard interface (CMIS) | `/cmis` browser binding | `GET http://localhost:8081/cmis/browser` with a Bearer token |
| Dev deployment: Docker Compose | `infra/docker-compose/` | Section 5 |
| Prod deployment: Kubernetes with health checks, replicas, persistence | `infra/k8s/` | Section 7; self-healing demos |
| All self-built images on Debian 13 | every `Dockerfile`, bootstrap runtime | `grep -i trixie */Dockerfile infra/*/Dockerfile*` |
| Self-hosted Git + CI/CD + OCI registry | Gitea + Actions (`.gitea/workflows/ci.yml`) | Section 7.4 |
| Automated tests for every component | `backend/src/test`, `frontend/src/**/*.test.*`, `ocr-worker/tests` | Section 6 |
| Reproducible from a clean clone with generated secrets | `generate-env.sh` / `generate-secrets.sh`, no committed credentials | This entire guide |
