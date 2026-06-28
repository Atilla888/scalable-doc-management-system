package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.dto.OcrJobResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public OcrAdminService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<OcrJobResponse> listJobs(String status) {
        List<DocumentRecord> records =
                status != null && !status.isBlank()
                        ? documentRepository.findByOcrStatusOrderByUploadDateDesc(status.trim())
                        : documentRepository.findByOcrStatusInOrderByUploadDateDesc(QUEUE_STATUSES);
        return records.stream().map(OcrJobResponse::from).toList();
    }

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
