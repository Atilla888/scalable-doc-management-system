# Kubernetes production deployment

This runs the whole DMS on a single-node minikube cluster: MongoDB, Keycloak, the
backend (REST + CMIS), the frontend, and the OCR worker. It behaves the same as the
Docker Compose dev setup — same auth, RBAC, search, and OCR — the difference is that
everything runs as Kubernetes workloads with health checks, persistent storage, and a
couple of replicas for the stateless parts.

MongoDB is the one stateful piece, so it runs as a StatefulSet with its own 5Gi volume
and the app connects to it as a limited user. Keycloak runs as a single deployment and
imports the realm on boot, with its health checks on port 9000. The backend runs two
replicas and won't accept traffic until Mongo is reachable. The frontend is two
replicas of nginx serving the React build. The OCR worker also runs two replicas — it
claims each job atomically, so a second one never steps on the first. Every image we
build ourselves is based on Debian 13 (trixie).

## Before you start

You need minikube already running on the VM (`minikube status` should be all green),
the `NO_PROXY` lines in your `~/.bashrc` so `kubectl` skips the university proxy when
talking to the cluster, and Docker usable without sudo (`docker run --rm hello-world`
should work).

## Deploying it

From the repo root on the VM, it's three steps. First generate the credentials — this
writes `infra/k8s/01-secrets.yaml` with random passwords and is only done once per
cluster, so copy down what it prints:

```bash
bash infra/k8s/generate-secrets.sh
```

Then build the images and load them into minikube, which takes a few minutes:

```bash
bash infra/k8s/build-and-load.sh
```

Finally deploy everything and wait for it to come up:

```bash
bash infra/k8s/deploy.sh
```

Once that finishes, every pod should be `Running` and `READY`. If something is stuck
on `Pending` or `CrashLoopBackOff`, see the end of this file.

Between Keycloak and the backend, `deploy.sh` runs the **keycloak-bootstrap Job**
(`03b-keycloak-bootstrap.yaml`, script from `infra/keycloak/bootstrap/` published as a
ConfigMap — the same file the Compose stack runs). Keycloak ≥ 24 drops user attributes
that the realm's User Profile does not declare, so after the realm import the Job
declares the managed `department` attribute, re-applies `department=ITDLZ` to the
manager/contributor/viewer demo users, and verifies the token mapper and the
`dms-admin-api` service-account roles. It authenticates with the Keycloak admin
credentials from `dms-secrets`, is idempotent (deploy.sh simply re-runs it on every
deploy), and `deploy.sh` aborts — printing the Job logs — before the backend is applied
if it fails, so the backend never serves traffic against an unconfigured realm.

A note on the secrets: the real Secret never goes into Git. `generate-secrets.sh`
writes it with fresh random passwords (Mongo root, the app user, Keycloak admin, the
`dms-admin-api` client secret, and the demo users' login password), and it's
git-ignored and mode `600`. The only thing in the repo is `secrets.example.yaml`,
which just lists the keys. Mongo bakes the app password in on first init, so only
regenerate on a clean cluster — and delete the Mongo volume first if you do.
On a cluster whose `01-secrets.yaml` predates the department feature, add the two new
keys (`keycloak-admin-client-secret`, `demo-user-password`) to the existing Secret by
hand — regenerating everything would break the existing Mongo data.

## Reaching the app from your laptop

minikube lives inside the VM, so its services aren't reachable from your laptop
directly. The way around it is three SSH tunnels that forward local ports to the
cluster's NodePorts. The local ports (5173, 8080, 8081) are chosen so the app's
`localhost` URLs and the Keycloak token issuer line up, which is why nothing has to be
rebuilt. Grab the minikube IP on the VM with `minikube ip`, then from the laptop:

```powershell
ssh -L 5173:<MINIKUBE_IP>:30573 `
    -L 8080:<MINIKUBE_IP>:30080 `
    -L 8081:<MINIKUBE_IP>:30081 `
    <VM_USER>@<VM_HOST>
```

Keep that SSH window open while you're using the app. The frontend is then at
`http://localhost:5173`, Keycloak at `http://localhost:8080`, and the API and CMIS
endpoints at `http://localhost:8081`.

Running the VM in VirtualBox with NAT networking (no directly reachable VM IP)? The
same tunnels work through a forwarded SSH port — the setup and the `-p` variant of
this command are explained in
[`docs/test-environment-setup.md`](../../docs/test-environment-setup.md) (section 3
and 7.3).

Signing in goes through Keycloak. The realm import provisions the four demo users
(`admin`, `manager`, `contributor`, `viewer` — all `@dms.local`) with the password from
the `demo-user-password` Secret key, and the bootstrap Job guarantees manager,
contributor, and viewer carry `department=ITDLZ`. Additional users can be provisioned
in the Keycloak admin console (or self-register); give each one a DMS role —
`dms_admin`, `dms_department_manager`, `dms_contributor`, or `dms_viewer` — which is
what the backend authorizes against, and assign departments from the app's `/admin`
page. The Keycloak admin console itself uses the bootstrap admin from the generated
Secret.

## Checking it works

A few quick commands tell you the state of things:

```bash
kubectl -n dms get pods                 # all Running / READY
kubectl -n dms get pvc                  # Mongo volume should be Bound
kubectl -n dms logs deploy/ocr-worker   # worker polling for jobs
kubectl -n dms exec deploy/backend -- curl -sf http://localhost:8081/health
```

For an end-to-end check, upload a scanned PDF in the UI and watch its status move from
`pending` to `processing` to `completed`, both in the worker logs and on the document
page.

The deployment is also a good place to show off how Kubernetes behaves. Deleting a
backend pod (`kubectl -n dms delete pod -l app=backend`) brings it straight back, and
with two replicas the API never goes down in the meantime. Deleting `mongodb-0` lets
the StatefulSet restart it with the volume reattached and the data intact. And you can
scale the worker on demand with `kubectl -n dms scale deploy/ocr-worker --replicas=3`.

## What we left simple, on purpose

Mongo is a single node. The data persists and the pod heals itself, but there's no
replica set, so there's no automatic primary failover. It is a StatefulSet, though, so
moving to a three-member replica set later is a configuration change rather than a
rewrite.

Keycloak is one replica running in `start-dev`, which is plenty for the auth flow here;
a clustered Keycloak with an external database was more than this project needed.

Ingress is included but isn't the default. `08-ingress.yaml` shows the host-based
routing pattern, but the NodePort and tunnel path is the one we actually test and
recommend, and the comment at the top of that file explains what host-based access
would take.

## When something's wrong

If `deploy.sh` aborts at the bootstrap step, read the printed Job logs (or
`kubectl -n dms logs job/keycloak-bootstrap`) — the script names the exact check that
failed (missing realm, wrong admin credentials, missing user, …). It is safe to fix
the cause and simply re-run `deploy.sh`. If demo users are missing their department
or tokens lack the `department` claim, the same logs say why.

If a pod is stuck on `ImagePullBackOff`, the image didn't make it into minikube —
re-run `build-and-load.sh` and confirm with `minikube image ls | grep dms-`. If
`kubectl` just hangs, your shell has lost `NO_PROXY`; run `source ~/.bashrc` and try
again. If the backend won't go `READY`, check `kubectl -n dms logs deploy/backend` —
it's usually still waiting on Mongo, so make sure `mongodb-0` is ready and the init ran
(`kubectl -n dms logs mongodb-0 | grep -i "DMS database initialized"`). And if the
login redirect fails, make sure all three tunnels are open and you're using
`http://localhost:5173` rather than the VM hostname, since the realm only allows
`localhost` redirects in this setup.
