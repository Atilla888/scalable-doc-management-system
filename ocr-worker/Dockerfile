# OCR worker: Debian slim base with Tesseract + Poppler, per Issue spec.
FROM debian:bookworm-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    # Debian's system Python is "externally managed"; allow pip to install here.
    PIP_BREAK_SYSTEM_PACKAGES=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        tesseract-ocr \
        poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt ./
RUN pip3 install -r requirements.txt

COPY config.py ocr.py worker.py ./

CMD ["python3", "worker.py"]
