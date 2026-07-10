package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * View of a document's OCR/text-extraction job for the admin OCR monitoring
 * endpoint: the source document identity plus job state — {@code ocrStatus},
 * {@code extractionMethod}, {@code retryCount} and any {@code ocrError}. Built
 * from a {@link DocumentRecord} via {@link #from(DocumentRecord)}.
 */
public record OcrJobResponse(
        String id,
        String title,
        String fileName,
        String contentType,
        String ocrStatus,
        String extractionMethod,
        int retryCount,
        String ocrError,
        Instant uploadDate) {

    public static OcrJobResponse from(DocumentRecord record) {
        return new OcrJobResponse(
                record.getId(),
                record.getTitle(),
                record.getFileName(),
                record.getContentType(),
                record.getOcrStatus(),
                record.getExtractionMethod(),
                record.getRetryCount(),
                record.getOcrError(),
                record.getUploadDate());
    }
}
