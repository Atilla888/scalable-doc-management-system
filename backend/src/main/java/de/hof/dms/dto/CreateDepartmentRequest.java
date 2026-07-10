package de.hof.dms.dto;

/**
 * Payload for {@code POST /api/admin/departments}.
 *
 * <p>The code is normalized (trimmed, upper-cased) and must be unique; it is
 * immutable after creation. {@code displayName} defaults to the code when
 * omitted.
 *
 * @param code the department code, e.g. {@code ITDLZ}
 * @param displayName optional human-readable name
 */
public record CreateDepartmentRequest(String code, String displayName) {}
