"""Runtime configuration for the OCR worker, sourced from environment variables."""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    """Worker settings. Defaults match the Docker Compose stack."""

    mongodb_uri: str
    poll_interval_ms: int
    languages: str
    max_file_mb: int
    max_pages: int

    @classmethod
    def from_env(cls) -> "Config":
        return cls(
            # The database name is taken from the URI path (".../dms").
            mongodb_uri=os.getenv("MONGODB_URI", "mongodb://mongodb:27017/dms"),
            poll_interval_ms=_positive_int("OCR_POLL_INTERVAL_MS", 5000),
            # Tesseract language packs; "eng" is the default install. Tuning beyond
            # this is out of scope for the MVP.
            languages=os.getenv("OCR_LANGUAGES", "eng"),
            max_file_mb=_positive_int("OCR_MAX_FILE_MB", 50),
            max_pages=_positive_int("OCR_MAX_PAGES", 200),
        )

    @property
    def poll_interval_seconds(self) -> float:
        return self.poll_interval_ms / 1000.0

    @property
    def max_file_bytes(self) -> int:
        return self.max_file_mb * 1024 * 1024


def _positive_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        value = int(raw)
    except ValueError:
        return default
    return value if value > 0 else default
