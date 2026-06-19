package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * A single search hit. Deliberately exposes only safe metadata — never ACL
 * fields and never the raw {@code ocr_text} (only a short snippet derived from
 * it). Built explicitly via {@link #from(DocumentRecord)} so no document field
 * can leak by accident.
 */
public record SearchResultEntry(
        String id,
        String title,
        String eapNumber,
        String documentType,
        String snippet,
        String parentFolderId,
        Instant updatedAt,
        String ocrStatus) {

    private static final int SNIPPET_MAX = 200;

    public static SearchResultEntry from(DocumentRecord record) {
        return new SearchResultEntry(
                record.getId(),
                record.getTitle(),
                record.getEapNumber(),
                record.getDocumentType(),
                buildSnippet(record),
                record.getFolderId(),
                record.getUploadDate(),
                record.getOcrStatus());
    }

    /** Short snippet from the description, falling back to OCR text, then the title. */
    private static String buildSnippet(DocumentRecord record) {
        String source = firstNonBlank(record.getDescription(), record.getOcrText());
        if (source == null) {
            return record.getTitle();
        }
        String trimmed = source.strip();
        return trimmed.length() <= SNIPPET_MAX
                ? trimmed
                : trimmed.substring(0, SNIPPET_MAX).strip() + "…";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
