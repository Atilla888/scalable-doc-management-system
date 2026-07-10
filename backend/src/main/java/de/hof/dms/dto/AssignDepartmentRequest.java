package de.hof.dms.dto;

/**
 * Payload for {@code PUT /api/admin/users/{userId}/department}.
 *
 * <p>A blank or {@code null} department removes the user's department
 * assignment; otherwise the value must be the code of an existing, active
 * department.
 *
 * @param department the department code to assign, or null/blank to remove
 */
public record AssignDepartmentRequest(String department) {}
