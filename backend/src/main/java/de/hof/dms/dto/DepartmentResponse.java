package de.hof.dms.dto;

import de.hof.dms.domain.Department;

import java.time.Instant;

/**
 * Response body describing one department from the registry.
 *
 * @param id the department's database identifier
 * @param code the normalized, unique department code (immutable)
 * @param displayName the human-readable department name
 * @param active whether the department may currently be assigned to users
 * @param createdAt when the department was created
 * @param updatedAt when the department was last modified
 */
public record DepartmentResponse(
        String id,
        String code,
        String displayName,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    /** Maps a persistent {@link Department} to its response representation. */
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getDisplayName(),
                department.isActive(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
