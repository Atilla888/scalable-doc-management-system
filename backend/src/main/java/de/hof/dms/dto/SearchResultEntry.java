package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * A single search hit. Deliberately exposes only safe metadata — never ACL
 * fields and never the raw {@code ocr_text} (only a short snippet derived from
 * it). Built explicitly via {@link #from(DocumentRecord)} so no document field
 * can leak by accident.
 *
 * @param id the document's unique identifier
 * @param title the document title
 * @param eapNumber the document's EAP number
 * @param documentType the document type
 * @param snippet a short context snippet derived from the document
 * @param parentFolderId the id of the folder containing the document
 * @param updatedAt the instant the document was last updated
 * @param ocrStatus the document's OCR-processing status
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
    private static final int SNIPPET_LEAD = 60;

    /**
     * Builds an entry with a term-agnostic snippet.
     *
     * @param record the source document
     * @return the safe search-result view
     */
    public static SearchResultEntry from(DocumentRecord record) {
        return from(record, null);
    }

    /**
     * Builds an entry whose snippet is centred on the first occurrence of
     * {@code term} (so OCR/description matches are shown in context). The term
     * itself is not wrapped here — highlighting is applied by the client to
     * avoid injecting markup into the payload.
     */
    public static SearchResultEntry from(DocumentRecord record, String term) {
        return new SearchResultEntry(
                record.getId(),
                record.getTitle(),
                record.getEapNumber(),
                record.getDocumentType(),
                buildSnippet(record, term),
                record.getFolderId(),
                record.getUploadDate(),
                record.getOcrStatus());
    }

    /** Short snippet from the description, falling back to OCR text, then the title. */
    private static String buildSnippet(DocumentRecord record, String term) {
        String source = firstNonBlank(record.getDescription(), record.getOcrText());
        if (source == null) {
            return record.getTitle();
        }
        String trimmed = source.strip();

        if (term != null && !term.isBlank()) {
            int idx = trimmed.toLowerCase().indexOf(term.trim().toLowerCase());
            if (idx >= 0) {
                int start = Math.max(0, idx - SNIPPET_LEAD);
                int end = Math.min(trimmed.length(), start + SNIPPET_MAX);
                String window = trimmed.substring(start, end).strip();
                return (start > 0 ? "… " : "") + window + (end < trimmed.length() ? " …" : "");
            }
        }

        return trimmed.length() <= SNIPPET_MAX
                ? trimmed
                : trimmed.substring(0, SNIPPET_MAX).strip() + "…";
    }

    /** Returns the first of the two arguments that is non-{@code null} and non-blank, or {@code null}. */
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
