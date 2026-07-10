package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * Compact document view for list displays (e.g. folder contents): identity,
 * title, EAP number, type, OCR/document status and upload date. Built from a
 * {@link DocumentRecord} via {@link #from(DocumentRecord)}.
 */
public record DocumentSummary(
        String id,
        String title,
        String eapNumber,
        String documentType,
        String ocrStatus,
        String documentStatus,
        Instant uploadDate) {

    public static DocumentSummary from(DocumentRecord record) {
        return new DocumentSummary(
                record.getId(),
                record.getTitle(),
                record.getEapNumber(),
                record.getDocumentType(),
                record.getOcrStatus(),
                record.getDocumentStatus(),
                record.getUploadDate());
    }
}
