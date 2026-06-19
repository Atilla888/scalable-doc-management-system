"""Text extraction from scanned documents using Tesseract.

Supported MVP inputs: scanned PDF, PNG, JPEG. PDFs are rasterized page by page
with Poppler (``pdftoppm`` via ``pdf2image``) before OCR. Heavy imports are done
lazily so callers that never touch real OCR (e.g. the failure-path unit test) do
not need Tesseract/Poppler installed.
"""

import io


class OcrError(Exception):
    """Raised when a document cannot be OCR'd (unsupported type or engine error)."""


def extract_text(
    data: bytes,
    content_type: str | None,
    file_name: str | None,
    languages: str = "eng",
) -> str:
    """Extract text from raw file bytes. Raises :class:`OcrError` on any failure."""
    kind = classify(content_type, file_name)
    if kind == "pdf":
        return _extract_pdf(data, languages)
    if kind == "image":
        return _extract_image(data, languages)
    raise OcrError(
        f"Unsupported input for OCR (content_type={content_type!r}, file_name={file_name!r}); "
        "MVP supports scanned PDF, PNG, and JPEG only"
    )


def classify(content_type: str | None, file_name: str | None) -> str:
    """Return 'pdf', 'image', or 'unknown' from content type / filename."""
    ct = (content_type or "").lower()
    name = (file_name or "").lower()
    if ct == "application/pdf" or name.endswith(".pdf"):
        return "pdf"
    if ct.startswith("image/") or name.endswith((".png", ".jpg", ".jpeg")):
        return "image"
    return "unknown"


def _extract_image(data: bytes, languages: str) -> str:
    from PIL import Image
    import pytesseract

    try:
        with Image.open(io.BytesIO(data)) as image:
            return pytesseract.image_to_string(image, lang=languages).strip()
    except OcrError:
        raise
    except Exception as exc:  # Pillow / Tesseract errors
        raise OcrError(f"Image OCR failed: {exc}") from exc


def _extract_pdf(data: bytes, languages: str) -> str:
    from pdf2image import convert_from_bytes
    import pytesseract

    try:
        pages = convert_from_bytes(data)
    except Exception as exc:  # Poppler rasterization errors
        raise OcrError(f"PDF rasterization failed: {exc}") from exc

    texts = []
    try:
        for page in pages:
            texts.append(pytesseract.image_to_string(page, lang=languages).strip())
    except Exception as exc:
        raise OcrError(f"PDF OCR failed: {exc}") from exc
    finally:
        for page in pages:
            page.close()

    return "\n\n".join(text for text in texts if text).strip()
