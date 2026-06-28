"""Unit tests for input classification and the embedded-vs-OCR routing.

These do not require Tesseract/Poppler/pdfminer at runtime — the unsupported branch
raises before any heavy import, and the routing tests stub the per-method helpers.
Real extraction is exercised by the Compose integration test in the README.
"""

import pytest

import ocr
from ocr import (
    METHOD_EMBEDDED,
    METHOD_OCR,
    OcrError,
    classify,
    extract_text,
)


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


def test_born_digital_pdf_uses_embedded_text(monkeypatch):
    monkeypatch.setattr(ocr, "_pdf_text_layer", lambda data: "A born-digital report.")
    monkeypatch.setattr(
        ocr, "_ocr_pdf", lambda data, lang: pytest.fail("OCR should be skipped")
    )

    result = extract_text(b"%PDF-fake", "application/pdf", "report.pdf")

    assert result.method == METHOD_EMBEDDED
    assert result.text == "A born-digital report."


def test_scanned_pdf_falls_back_to_ocr(monkeypatch):
    monkeypatch.setattr(ocr, "_pdf_text_layer", lambda data: "")
    monkeypatch.setattr(ocr, "_ocr_pdf", lambda data, lang: "ocr text from scan")

    result = extract_text(b"%PDF-scan", "application/pdf", "scan.pdf")

    assert result.method == METHOD_OCR
    assert result.text == "ocr text from scan"


def test_thin_text_layer_is_treated_as_scan(monkeypatch):
    monkeypatch.setattr(ocr, "_pdf_text_layer", lambda data: "p. 1")
    monkeypatch.setattr(ocr, "_ocr_pdf", lambda data, lang: "real scanned text")

    result = extract_text(b"%PDF-scan", "application/pdf", "scan.pdf")

    assert result.method == METHOD_OCR
    assert result.text == "real scanned text"


def test_image_always_uses_ocr(monkeypatch):
    monkeypatch.setattr(ocr, "_ocr_image", lambda data, lang: "text from photo")

    result = extract_text(b"\x89PNG", "image/png", "photo.png")

    assert result.method == METHOD_OCR
    assert result.text == "text from photo"
