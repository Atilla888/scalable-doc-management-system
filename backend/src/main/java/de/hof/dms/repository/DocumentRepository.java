package de.hof.dms.repository;

import de.hof.dms.domain.DocumentRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentRecord, String> {

    Optional<DocumentRecord> findByEapNumber(String eapNumber);

    List<DocumentRecord> findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
            String folderId, String documentStatus);
}
