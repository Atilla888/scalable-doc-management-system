package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

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
