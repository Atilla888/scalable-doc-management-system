package de.hof.dms.repository;

import de.hof.dms.domain.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FolderRepository extends MongoRepository<Folder, String> {

    Optional<Folder> findByPath(String path);
}
