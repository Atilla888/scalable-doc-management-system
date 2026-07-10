package de.hof.dms.repository;

import de.hof.dms.domain.Department;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for {@link Department} entities, the application's
 * department registry keyed by the normalized department code.
 */
public interface DepartmentRepository extends MongoRepository<Department, String> {

    /** Finds a department by its normalized code. */
    Optional<Department> findByCode(String code);

    /** Returns whether a department with the given normalized code exists. */
    boolean existsByCode(String code);

    /** Fetches all departments ordered by code. */
    List<Department> findAllByOrderByCodeAsc();
}
