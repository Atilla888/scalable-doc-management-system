"""Unit tests for the worker loop using an in-memory fake of the documents collection.

Covered:
- empty queue -> process_one() returns False
- happy path -> claim sets 'processing', then result write sets 'completed'/'indexed'
- failure path -> sets 'failed' with an ocr_error message, queue stays drainable
"""

from config import Config
from ocr import METHOD_EMBEDDED, METHOD_OCR, ExtractionResult
from worker import COMPLETED, FAILED, INDEXED, PENDING, PROCESSING, Worker


class FakeDocuments:
    """Minimal stand-in for a pymongo collection with the two methods we use."""

    def __init__(self, docs):
        self.docs = docs

    def find_one_and_update(self, filt, update, sort=None, return_document=None):
        for doc in sorted(self.docs, key=lambda d: d.get("upload_date", 0)):
            if doc.get("ocr_status") == filt.get("ocr_status"):
                doc.update(update["$set"])
                return dict(doc)
        return None

    def update_one(self, filt, update):
        for doc in self.docs:
            if doc["_id"] == filt["_id"]:
                doc.update(update.get("$set", {}))
                for key in update.get("$unset", {}):
                    doc.pop(key, None)
        return None


def _worker(docs, fs=None):
    worker = Worker(Config("mongodb://localhost:27017/dms", 5000, "eng"))
    worker.documents = FakeDocuments(docs)
    worker.fs = fs
    return worker


def test_process_one_returns_false_when_no_pending():
    worker = _worker([{"_id": "1", "ocr_status": COMPLETED}])
    assert worker.process_one() is False


def test_failure_path_sets_failed_with_error():
    # No gridfs_file_id -> _ocr_document raises OcrError -> marked failed.
    doc = {"_id": "1", "ocr_status": PENDING, "upload_date": 1}
    worker = _worker([doc])

    handled = worker.process_one()

    assert handled is True
    assert doc["ocr_status"] == FAILED
    assert doc["indexing_status"] == FAILED
    assert "ocr_error" in doc and doc["ocr_error"]


def test_one_failure_does_not_block_the_next_job():
    bad = {"_id": "1", "ocr_status": PENDING, "upload_date": 1}
    good = {
        "_id": "2",
        "ocr_status": PENDING,
        "upload_date": 2,
        "gridfs_file_id": "stub",
        "content_type": "image/png",
        "file_name": "scan.png",
    }
    worker = _worker([bad, good])

    # Stub OCR so the "good" doc completes without Tesseract.
    worker._ocr_document = lambda d: ExtractionResult("hello world", METHOD_OCR) if d["_id"] == good["_id"] else (_ for _ in ()).throw(
        Exception("boom")
    )

    assert worker.process_one() is True  # bad -> failed
    assert worker.process_one() is True  # good -> completed
    assert worker.process_one() is False  # queue drained

    assert bad["ocr_status"] == FAILED
    assert good["ocr_status"] == COMPLETED
    assert good["indexing_status"] == INDEXED
    assert good["ocr_text"] == "hello world"
    assert good["extraction_method"] == METHOD_OCR


def test_claim_marks_processing_before_ocr():
    seen = {}
    doc = {"_id": "1", "ocr_status": PENDING, "upload_date": 1}
    worker = _worker([doc])

    def capture(d):
        # status seen by the OCR stage must already be 'processing'
        seen["status"] = d["ocr_status"]
        return ExtractionResult("text", METHOD_EMBEDDED)

    worker._ocr_document = capture
    worker.process_one()

    assert seen["status"] == PROCESSING
    assert doc["ocr_status"] == COMPLETED
    assert doc["extraction_method"] == METHOD_EMBEDDED
