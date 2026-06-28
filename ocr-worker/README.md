# OCR worker

A standalone background service that makes documents full-text searchable. It polls
MongoDB for documents with `ocr_status = "pending"` and extracts their text: for
born-digital PDFs it reads the embedded text layer directly, and it falls back to
[Tesseract](https://github.com/tesseract-ocr/tesseract) OCR only for scans. The text
is stored in `documents.ocr_text`, the method used in `documents.extraction_method`,
and the document is marked `completed` / `indexed`.

There is no message queue — polling is sufficient for the MVP.

## How it works

Each poll cycle the worker:

1. **Claims** the oldest pending document atomically with `find_one_and_update`
   (filter `ocr_status = "pending"`, sorted by `upload_date` ascending), flipping it
   to `processing`. The atomic update guarantees two workers never grab the same job.
2. **Fetches** the binary from GridFS using `gridfs_file_id`.
3. **Extracts text**, choosing the cheapest method:
   - Born-digital PDF (has a text layer) → text read directly with `pdfminer.six`, no OCR.
   - Scanned PDF (no text layer) → rasterized page by page with Poppler (`pdftoppm` via
     `pdf2image`), then Tesseract per page.
   - PNG / JPEG → Tesseract directly.
4. **Writes** the result: `ocr_text`, `extraction_method` (`embedded` or `ocr`),
   `ocr_status = "completed"`, `indexing_status = "indexed"`.

On any failure the document is set to `ocr_status = "failed"` with an `ocr_error`
message; the worker logs it and continues, so one bad scan never blocks the queue.
If MongoDB is temporarily unreachable the error is caught and retried on the next
poll cycle — the process does not crash.

Supported MVP inputs: PDF (born-digital or scanned), PNG, JPEG. XML/JSON and other non-image types are
out of scope (the backend marks those `ocr_status = "not_required"`, so they are
never picked up).

## Configuration

| Env var                | Default                          | Purpose                                   |
| ---------------------- | -------------------------------- | ----------------------------------------- |
| `MONGODB_URI`          | `mongodb://mongodb:27017/dms`    | Connection string (DB name from the path) |
| `OCR_POLL_INTERVAL_MS` | `5000`                           | Delay between poll cycles, milliseconds   |
| `OCR_LANGUAGES`        | `eng`                            | Tesseract language(s), e.g. `eng+deu`     |

## Run with Docker Compose

The worker is wired into the stack as the `ocr-worker` service. From the repo root:

```bash
cd infra/docker-compose
docker compose up --build ocr-worker
```

Or bring up the whole stack (`docker compose up --build`) and watch the logs:

```bash
docker compose logs -f ocr-worker
```

Upload a scan via the frontend (`/upload`) or `POST /api/documents`, then watch the
status transition `pending → processing → completed` in the logs and on the document
detail page.

## Tests

The worker's own unit tests use a fake collection and need no Tesseract/Poppler:

```bash
cd ocr-worker
pip install -r requirements-dev.txt
pytest
```

`tests/test_worker.py` covers the claim→complete happy path, the failure path
(`ocr_status = "failed"` with `ocr_error`), and that one failure does not block the
next job. `tests/test_ocr.py` covers input classification, the embedded-vs-OCR
routing, and the unsupported-type error. End-to-end OCR (real Tesseract on a known scan, plus the authorized/
unauthorized `/api/search` checks) is exercised against the running Compose stack.
