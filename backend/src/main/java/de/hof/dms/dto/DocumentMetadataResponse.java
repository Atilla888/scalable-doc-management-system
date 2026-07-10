package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * Full metadata view of a document returned by the document-detail endpoint.
 * Exposes descriptive fields, upload info and processing-state values
 * (OCR/extraction/indexing/document status) from a {@link DocumentRecord},
 * without the binary content or ACL. Built via {@link #from(DocumentRecord)}.
 *
 * @param id                 unique identifier of the document
 * @param title              display title of the document
 * @param description        free-text description of the document
 * @param documentType       document-type classification value
 * @param fileName           original file name of the uploaded content
 * @param contentType        MIME type of the stored content
 * @param fileSize           size of the stored content in bytes
 * @param uploadDate         timestamp when the document was uploaded
 * @param uploaderId         identifier of the user who uploaded the document
 * @param organizationalUnit organizational unit the document belongs to
 * @param eapNumber          EAP number assigned to the document
 * @param parentId           identifier of the folder containing the document
 * @param ocrStatus          status of the OCR/text-extraction job
 * @param extractionMethod   method used to extract the document's text
 * @param textPreview        short preview of the extracted text, only exposed
 *                           after the caller has READ permission
 * @param indexingStatus     status of the document's search-index processing
 * @param documentStatus     overall lifecycle status of the document
 */
public record DocumentMetadataResponse(
        String id,
        String title,
        String description,
        String documentType,
        String fileName,
        String contentType,
        long fileSize,
        Instant uploadDate,
        String uploaderId,
        String organizationalUnit,
        String eapNumber,
        String parentId,
        String ocrStatus,
        String extractionMethod,
        String textPreview,
        String indexingStatus,
        String documentStatus) {

    private static final int TEXT_PREVIEW_MAX = 1600;

    /**
     * Builds a {@link DocumentMetadataResponse} from a {@link DocumentRecord} entity.
     *
     * @param record the document entity to expose metadata for
     * @return a full metadata view of the given document
     */
    public static DocumentMetadataResponse from(DocumentRecord record) {
        return new DocumentMetadataResponse(
                record.getId(),
                record.getTitle(),
                record.getDescription(),
                record.getDocumentType(),
                record.getFileName(),
                record.getContentType(),
                record.getFileSize(),
                record.getUploadDate(),
                record.getUploaderId(),
                record.getOrganizationalUnit(),
                record.getEapNumber(),
                record.getFolderId(),
                record.getOcrStatus(),
                record.getExtractionMethod(),
                preview(record.getOcrText()),
                record.getIndexingStatus(),
                record.getDocumentStatus());
    }

    /**
     * Returns a compact display preview of extracted text. The full OCR text can
     * be large, so the detail API exposes only a bounded window for UI display.
     *
     * @param text extracted OCR/text content
     * @return a trimmed preview, or {@code null} when no text is available
     */
    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TEXT_PREVIEW_MAX) {
            return normalized;
        }
        return normalized.substring(0, TEXT_PREVIEW_MAX).strip() + "…";
    }
}
