# Documentation artifacts

Generated documentation for the DMS project. All artifacts are static HTML and
can be opened directly in a browser (no server required).

| Artifact | What it covers | Open |
| --- | --- | --- |
| `api.html` | REST + CMIS API reference (Redoc, rendered from `openapi.json`) | `docs/api.html` |
| `openapi.json` | OpenAPI 3 specification of the backend API | — |
| `backend/` | Backend Javadoc (Java classes, services, controllers) | `docs/backend/index.html` |
| `frontend/` | Frontend JSDoc (React components, hooks, API client) | `docs/frontend/index.html` |
| `ocr-worker/` | OCR worker Python API docs (pdoc) | `docs/ocr-worker/index.html` |

The API is also served live by the running backend at
`/swagger-ui.html` (interactive Swagger UI) and `/v3/api-docs` (raw spec).

## Regenerating

Documentation is generated from the source code and its docstrings. Regenerate
after code changes.

### API reference (`api.html` + `openapi.json`)

With the backend running (e.g. via `infra/docker-compose`):

```bash
curl -s http://localhost:8081/v3/api-docs > docs/openapi.json
npx @redocly/cli build-docs docs/openapi.json -o docs/api.html
```

### Backend Javadoc (`backend/`)

```bash
cd backend
./mvnw -B javadoc:javadoc
rm -rf ../docs/backend && cp -r target/reports/apidocs ../docs/backend
```

### Frontend JSDoc (`frontend/`)

```bash
cd frontend
npx documentation build "src/**/*.{js,jsx}" -f html -o ../docs/frontend --shallow
```

### OCR worker (`ocr-worker/`)

```bash
cd ocr-worker
pip install -r requirements-dev.txt   # includes pdoc
pdoc config.py ocr.py worker.py -o ../docs/ocr-worker
```
