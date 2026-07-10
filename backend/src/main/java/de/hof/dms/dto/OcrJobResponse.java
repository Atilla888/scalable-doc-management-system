package de.hof.dms.dto;

import de.hof.dms.domain.DocumentRecord;

import java.time.Instant;

/**
 * View of a document's OCR/text-extraction job for the admin OCR monitoring
 * endpoint: the source document identity plus job state — {@code ocrStatus},
 * {@code extractionMethod}, {@code retryCount} and any {@code ocrError}. Built
 * from a {@link DocumentRecord} via {@link #from(DocumentRecord)}.
 *
 * @param id               unique identifier of the source document
 * @param title            display title of the source document
 * @param fileName         original file name of the uploaded content
 * @param contentType      MIME type of the stored content
 * @param ocrStatus        current status of the OCR/text-extraction job
 * @param extractionMethod method used to extract the document's text
 * @param retryCount       number of times the extraction job has been retried
 * @param ocrError         error message from the last failed extraction attempt, if any
 * @param uploadDate       timestamp when the document was uploaded
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

    /**
     * Builds an {@link OcrJobResponse} from a {@link DocumentRecord} entity.
     *
     * @param record the document entity whose OCR job state is exposed
     * @return a view of the given document's OCR/text-extraction job
     */
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
