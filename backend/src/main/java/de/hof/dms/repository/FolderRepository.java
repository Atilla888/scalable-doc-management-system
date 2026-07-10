package de.hof.dms.repository;

import de.hof.dms.domain.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Folder} entities, supporting path- and
 * parent-based lookups over the materialized-path folder tree.
 */
public interface FolderRepository extends MongoRepository<Folder, String> {

    /** Finds the folder at an exact materialized path (e.g. the root {@code "/"}). */
    Optional<Folder> findByPath(String path);

    /** Fetches the direct child folders of a parent, ordered by name. */
    List<Folder> findByParentIdOrderByNameAsc(String parentId);

    /**
     * Fetches a folder and its whole subtree, which share the same
     * materialized-path prefix.
     */
    List<Folder> findByPathStartingWith(String pathPrefix);

    /** Returns whether any folder's ACL names the department as owner. */
    boolean existsByAclOwnerDepartment(String department);

    /** Returns whether any folder's ACL lists the department as allowed. */
    boolean existsByAclAllowedDepartments(String department);
}
