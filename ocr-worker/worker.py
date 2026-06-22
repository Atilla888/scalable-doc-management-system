"""OCR worker: polls MongoDB for pending documents, OCRs them, stores the text.

The worker is a standalone process (its own container). It repeatedly:

1. Atomically claims the oldest ``ocr_status = "pending"`` document
   (``find_one_and_update`` flips it to ``processing`` so two workers never
   pick the same job).
2. Streams the file out of GridFS by ``gridfs_file_id``.
3. Runs Tesseract (see :mod:`ocr`).
4. Writes ``ocr_text`` and sets ``ocr_status = "completed"`` /
   ``indexing_status = "indexed"``.

Failures mark the single document ``failed`` (with ``ocr_error``) and never crash
the loop, so one bad scan does not block the queue. A MongoDB outage is caught and
retried on the next poll cycle.
"""

import logging
import signal
import time
from datetime import datetime, timezone

import gridfs
from gridfs.errors import NoFile
from bson import ObjectId
from bson.errors import InvalidId
from pymongo import ASCENDING, MongoClient, ReturnDocument
from pymongo.errors import PyMongoError

from config import Config
from ocr import OcrError, extract_text

log = logging.getLogger("ocr-worker")

# Status vocabulary shared with the backend and the frontend OcrStatusBadge.
PENDING = "pending"
PROCESSING = "processing"
COMPLETED = "completed"
FAILED = "failed"
INDEXED = "indexed"
INDEXING_FAILED = "failed"

# Mongo server selection timeout: keep it short so an outage surfaces as a
# retryable error within a poll cycle instead of hanging the worker.
SERVER_SELECTION_TIMEOUT_MS = 5000


def _now():
    return datetime.now(timezone.utc)


class Worker:
    def __init__(self, config: Config):
        self.config = config
        self._running = True
        self.client = None
        self.documents = None
        self.fs = None

    # -- lifecycle ---------------------------------------------------------

    def connect(self) -> None:
        self.client = MongoClient(
            self.config.mongodb_uri,
            serverSelectionTimeoutMS=SERVER_SELECTION_TIMEOUT_MS,
        )
        db = self.client.get_default_database()
        if db is None:
            raise ValueError(
                "MONGODB_URI must include a database name, e.g. mongodb://host:27017/dms"
            )
        self.documents = db["documents"]
        self.fs = gridfs.GridFS(db)

    def stop(self, *_args) -> None:
        log.info("Shutdown signal received; finishing current cycle")
        self._running = False

    def run(self) -> None:
        if self.documents is None:
            self.connect()
        log.info(
            "OCR worker started (poll interval %d ms, languages=%s)",
            self.config.poll_interval_ms,
            self.config.languages,
        )
        while self._running:
            try:
                # Drain all currently-pending jobs, then sleep until the next poll.
                while self._running and self.process_one():
                    pass
            except PyMongoError as exc:
                # MongoDB temporarily unreachable: log and retry next cycle.
                log.warning("MongoDB error, will retry after poll interval: %s", exc)
            except Exception:  # never let the loop die
                log.exception("Unexpected error in poll loop")
            self._interruptible_sleep()
        log.info("OCR worker stopped")

    # -- one unit of work --------------------------------------------------

    def claim_next(self):
        """Atomically grab the oldest pending document and mark it processing."""
        return self.documents.find_one_and_update(
            {"ocr_status": PENDING},
            {"$set": {"ocr_status": PROCESSING, "ocr_started_at": _now()}},
            sort=[("upload_date", ASCENDING)],
            return_document=ReturnDocument.AFTER,
        )

    def process_one(self) -> bool:
        """Process a single pending document. Returns False when the queue is empty."""
        doc = self.claim_next()
        if doc is None:
            return False

        doc_id = doc.get("_id")
        try:
            text = self._ocr_document(doc)
            self.documents.update_one(
                {"_id": doc_id},
                {
                    "$set": {
                        "ocr_text": text,
                        "ocr_status": COMPLETED,
                        "indexing_status": INDEXED,
                        "ocr_completed_at": _now(),
                    },
                    "$unset": {"ocr_error": ""},
                },
            )
            log.info("OCR completed for %s (%d chars extracted)", doc_id, len(text))
        except PyMongoError:
            # Writing the result failed; let the outer loop handle the outage.
            # The document stays in 'processing' and would need requeueing, but we
            # do not lose the worker. Re-raise so run() logs and backs off.
            raise
        except Exception as exc:
            self._mark_failed(doc_id, exc)
        return True

    def _ocr_document(self, doc) -> str:
        file_id = doc.get("gridfs_file_id")
        if not file_id:
            raise OcrError("Document has no gridfs_file_id")
        try:
            grid_out = self.fs.get(ObjectId(file_id))
        except InvalidId as exc:
            raise OcrError(f"Invalid gridfs_file_id {file_id!r}: {exc}") from exc
        except NoFile as exc:
            raise OcrError(f"GridFS file {file_id} not found") from exc
        data = grid_out.read()
        return extract_text(
            data,
            doc.get("content_type"),
            doc.get("file_name"),
            self.config.languages,
        )

    def _mark_failed(self, doc_id, error: Exception) -> None:
        log.warning("OCR failed for %s: %s", doc_id, error)
        try:
            self.documents.update_one(
                {"_id": doc_id},
                {
                    "$set": {
                        "ocr_status": FAILED,
                        "indexing_status": INDEXING_FAILED,
                        "ocr_error": str(error),
                        "ocr_completed_at": _now(),
                    }
                },
            )
        except PyMongoError:
            log.exception("Could not record OCR failure for %s", doc_id)

    # -- helpers -----------------------------------------------------------

    def _interruptible_sleep(self) -> None:
        """Sleep for the poll interval but wake promptly on a shutdown signal."""
        deadline = self.config.poll_interval_seconds
        step = 0.25
        slept = 0.0
        while self._running and slept < deadline:
            time.sleep(min(step, deadline - slept))
            slept += step


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    worker = Worker(Config.from_env())
    signal.signal(signal.SIGTERM, worker.stop)
    signal.signal(signal.SIGINT, worker.stop)
    worker.run()


if __name__ == "__main__":
    main()
