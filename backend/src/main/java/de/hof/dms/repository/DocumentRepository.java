package de.hof.dms.repository;

import de.hof.dms.domain.DocumentRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link DocumentRecord} entities, providing lookups by
 * EAP number, folder, and OCR status used across the document and OCR services.
 */
public interface DocumentRepository extends MongoRepository<DocumentRecord, String> {

    /** Finds the single document assigned the given unique EAP number, if any. */
    Optional<DocumentRecord> findByEapNumber(String eapNumber);

    /** Fetches documents in a folder with the given status, newest upload first. */
    List<DocumentRecord> findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
            String folderId, String documentStatus);

    /** Fetches documents whose OCR status is any of the given values, newest upload first. */
    List<DocumentRecord> findByOcrStatusInOrderByUploadDateDesc(List<String> ocrStatuses);

    /** Fetches documents with the given OCR status, newest upload first. */
    List<DocumentRecord> findByOcrStatusOrderByUploadDateDesc(String ocrStatus);
}
