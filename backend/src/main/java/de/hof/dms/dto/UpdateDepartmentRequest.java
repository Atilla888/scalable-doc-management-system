package de.hof.dms.dto;

/**
 * Payload for {@code PUT /api/admin/departments/{code}}.
 *
 * <p>Only the display name and active flag are editable; the code is immutable
 * because documents, folders, and ACLs reference it by value. Fields left
 * {@code null} keep their current value.
 *
 * @param displayName the new human-readable name, or null to keep the current one
 * @param active the new active flag, or null to keep the current one
 */
public record UpdateDepartmentRequest(String displayName, Boolean active) {}
