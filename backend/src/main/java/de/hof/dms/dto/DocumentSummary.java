package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * Compact document view for list displays (e.g. folder contents): identity,
 * title, EAP number, type, OCR/document status and upload date. Built from a
 * {@link DocumentRecord} via {@link #from(DocumentRecord)}.
 *
 * @param id             unique identifier of the document
 * @param title          display title of the document
 * @param eapNumber      EAP number assigned to the document
 * @param documentType   document-type classification value
 * @param ocrStatus      status of the document's OCR/text-extraction job
 * @param documentStatus overall lifecycle status of the document
 * @param uploadDate     timestamp when the document was uploaded
 */
public record DocumentSummary(
        String id,
        String title,
        String eapNumber,
        String documentType,
        String ocrStatus,
        String documentStatus,
        Instant uploadDate) {

    /**
     * Builds a {@link DocumentSummary} from a {@link DocumentRecord} entity.
     *
     * @param record the document entity to summarize
     * @return a compact summary of the given document
     */
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
