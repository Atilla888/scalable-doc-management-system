package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.OcrJobResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Administrative view over the OCR processing queue. Lists OCR jobs by status
 * and allows re-queuing failed jobs. OCR status values progress
 * {@code pending → processing → completed}, or {@code failed} on error.
 */
@Service
@Profile("!no-mongo")
public class OcrAdminService {

    public static final String OCR_PENDING = "pending";
    public static final String OCR_PROCESSING = "processing";
    public static final String OCR_COMPLETED = "completed";
    public static final String OCR_FAILED = "failed";
    public static final String INDEXING_PENDING = "pending";

    private static final List<String> QUEUE_STATUSES =
            List.of(OCR_PENDING, OCR_PROCESSING, OCR_FAILED, OCR_COMPLETED);

    private final DocumentRepository documentRepository;

    /**
     * Creates the service with the document repository backing the OCR queue.
     */
    public OcrAdminService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Lists OCR jobs, filtered to a single status when one is given, otherwise
     * returning all jobs in the OCR queue, newest upload first.
     */
    public List<OcrJobResponse> listJobs(String status) {
        List<DocumentRecord> records =
                status != null && !status.isBlank()
                        ? documentRepository.findByOcrStatusOrderByUploadDateDesc(status.trim())
                        : documentRepository.findByOcrStatusInOrderByUploadDateDesc(QUEUE_STATUSES);
        return records.stream().map(OcrJobResponse::from).toList();
    }

    /**
     * Re-queues a failed OCR job by resetting it to {@code pending} and clearing
     * its error, so the worker will process it again.
     *
     * @throws ApiException with 404 if the document is missing, or 409 if the job
     *     is not in the {@code failed} state
     */
    public OcrJobResponse retry(String id) {
        DocumentRecord record =
                documentRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!OCR_FAILED.equals(record.getOcrStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Only failed OCR jobs can be retried");
        }
        record.setOcrStatus(OCR_PENDING);
        record.setIndexingStatus(INDEXING_PENDING);
        record.setOcrError(null);
        DocumentRecord saved = documentRepository.save(record);
        return OcrJobResponse.from(saved);
    }
}
