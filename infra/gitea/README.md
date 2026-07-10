# Gitea: in-cluster Git, CI/CD, and OCI registry

Gitea is a required part of the production infrastructure. In this project it plays
all three roles the assignment asks for:

1. **Hosts the Git repository**, the same repo the team works in, served in-cluster.
2. **Automates build and rollout** through **Gitea Actions**, the pipeline in
   [`.gitea/workflows/ci.yml`](../../.gitea/workflows/ci.yml) tests every component,
   builds the images, and rolls the Kubernetes Deployments.
3. **Provides an OCI registry**, Gitea Packages stores the built container images,
   which the rollout step then deploys.

Everything runs on Debian 13 images we build ourselves (`dms-gitea:local`,
`dms-act-runner:local`); only the Docker-in-Docker sidecar is a third-party image
(`docker:27-dind`), consistent with how Mongo and Keycloak are treated.

## Components

| Piece | What it is | Manifest |
|-------|-----------|----------|
| `gitea` Deployment + PVC + NodePort Service | Gitea server (web, API, SSH, registry, Actions) | [`../k8s/07-gitea.yaml`](../k8s/07-gitea.yaml) |
| `gitea-runner` Deployment (`runner` + `dind`) | Actions runner in host mode + its Docker engine | same file |
| `gitea-deployer` ServiceAccount + Role + RoleBinding | lets the pipeline roll Deployments in `dms` only | same file |
| [`Dockerfile`](Dockerfile) / [`entrypoint.sh`](entrypoint.sh) | Debian 13 Gitea image |, |
| [`act-runner.Dockerfile`](act-runner.Dockerfile) / [`act-runner-entrypoint.sh`](act-runner-entrypoint.sh) | Debian 13 runner image (JDK 21, Node, Python, Docker CLI, kubectl) |, |

## How the pipeline works

The runner registers with Gitea once (its `.runner` state is kept on a PVC) and runs
workflow steps in **host mode**, directly in the runner container, which already has
the whole toolchain. Image builds are sent to the **dind** sidecar over
`DOCKER_HOST=tcp://localhost:2375`, so the build never touches the node's runtime and
the setup behaves the same on minikube and kind.

On every push to `main` the job:

1. checks out the repo from the in-cluster Gitea (no dependency on github.com);
2. runs `./mvnw verify` (backend), `pytest` (OCR worker), and `npm test`/`npm run build`
   (frontend), the build fails if any test fails;
3. builds the three Debian 13 images and pushes them to the Gitea registry at
   `<nodeIP>:30300/<owner>/dms-{backend,frontend,ocr-worker}`;
4. rolls the `backend`, `frontend`, and `ocr-worker` Deployments to the new images
   with `kubectl set image` + `rollout status`, using the scoped `gitea-deployer`
   ServiceAccount.

## Bringing it up

Gitea is applied by the normal `deploy.sh`. Two things can only be done once Gitea is
running, so they live in a separate step:

```bash
bash infra/k8s/bootstrap-gitea.sh
```

It mints the Actions **runner registration token** (Gitea generates it at runtime),
patches it into `dms-secrets` so the runner can register, creates the
`gitea-registry` **image-pull secret**, and attaches that secret to the app
Deployments so the kubelet can pull CI-built images.

Then, through the SSH tunnel (`http://localhost:3000`):

1. Log in as the Gitea admin (`dmsadmin` / the password from `generate-secrets.sh`).
2. Create a user or org `dms` and a repository `dms`.
3. Push the code and watch the pipeline under the repo's **Actions** tab:
   ```bash
   git remote add gitea http://localhost:3000/dms/dms.git
   git push gitea main
   ```

## The one host prerequisite

The kubelet pulls the CI-built images from the Gitea registry over plaintext HTTP on
the node's NodePort, so the container runtime must treat that address as insecure.
Start minikube accordingly, e.g.:

```bash
minikube start --insecure-registry "192.168.49.0/24"
```

(Use the subnet your node IP is on, `minikube ip` tells you; `bootstrap-gitea.sh`
prints the exact `--insecure-registry` value for your cluster.) The dind sidecar is
already configured with matching `--insecure-registry` ranges.

If you only need to demonstrate Gitea as the repository host and OCI registry without
the automated rollout, the manual `build-and-load.sh` path stays available and needs
none of this.

## Why host mode + dind

Running steps in the runner container (host mode) avoids pulling a fresh job image on
every run and keeps builds fast and offline-friendly. The dind sidecar gives the
build an isolated Docker engine, so nothing depends on whether the cluster node runs
Docker or containerd, the same manifest works on both minikube and kind.
