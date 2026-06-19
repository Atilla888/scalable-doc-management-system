"""Unit tests for input classification and the unsupported-type failure path.

These do not require Tesseract/Poppler — the unsupported branch raises before any
heavy import. Real OCR is exercised by the Compose integration test in the README.
"""

import pytest

from ocr import OcrError, classify, extract_text


def test_classify_pdf_by_content_type():
    assert classify("application/pdf", "anything") == "pdf"


def test_classify_pdf_by_extension():
    assert classify(None, "scan.PDF") == "pdf"


def test_classify_image_by_content_type():
    assert classify("image/png", "x") == "image"
    assert classify("image/jpeg", "x") == "image"


def test_classify_image_by_extension():
    assert classify(None, "scan.PNG") == "image"
    assert classify("", "photo.jpg") == "image"


def test_classify_unknown():
    assert classify("application/json", "data.json") == "unknown"
    assert classify(None, None) == "unknown"


def test_extract_text_unsupported_type_raises():
    with pytest.raises(OcrError):
        extract_text(b"some-bytes", "application/json", "data.json")
