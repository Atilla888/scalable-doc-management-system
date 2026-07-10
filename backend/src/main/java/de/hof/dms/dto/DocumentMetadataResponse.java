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
        String indexingStatus,
        String documentStatus) {

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
                record.getIndexingStatus(),
                record.getDocumentStatus());
    }
}
