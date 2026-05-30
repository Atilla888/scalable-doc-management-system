package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

public record SearchResultEntry(
        String id,
        String title,
        String eapNumber,
        String documentType,
        String ocrStatus,
        String snippet) {

    public static SearchResultEntry from(DocumentRecord record) {
        String snippet =
                record.getDescription() != null && !record.getDescription().isBlank()
                        ? record.getDescription()
                        : record.getTitle();
        return new SearchResultEntry(
                record.getId(),
                record.getTitle(),
                record.getEapNumber(),
                record.getDocumentType(),
                record.getOcrStatus(),
                snippet);
    }
}
