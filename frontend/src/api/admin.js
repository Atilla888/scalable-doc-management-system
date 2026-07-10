/**
 * @module api/admin
 * Admin console API calls.
 */
import { apiClient } from "./client";

/**
 * Fetches the administration overview (users, roles, permission scopes,
 * component health, and aggregate metrics).
 * GET /api/admin/overview
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Object>} The admin overview payload.
 */
export function getAdminOverview(signal) {
  return apiClient.get("/api/admin/overview", { signal });
}

/**
 * Lists all departments in the registry, ordered by code.
 * GET /api/admin/departments
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Array<Object>>} The departments.
 */
export function listDepartments(signal) {
  return apiClient.get("/api/admin/departments", { signal });
}

/**
 * Creates a department. The code is normalized server-side and must be unique.
 * POST /api/admin/departments
 * @param {{code: string, displayName?: string}} payload Creation payload.
 * @returns {Promise<Object>} The created department.
 */
export function createDepartment(payload) {
  return apiClient.post("/api/admin/departments", payload);
}

/**
 * Updates a department's display name and/or active flag (the code is immutable).
 * PUT /api/admin/departments/{code}
 * @param {string} code Department code.
 * @param {{displayName?: string, active?: boolean}} payload Fields to change.
 * @returns {Promise<Object>} The updated department.
 */
export function updateDepartment(code, payload) {
  return apiClient.put(`/api/admin/departments/${encodeURIComponent(code)}`, payload);
}

/**
 * Deletes a department that is no longer referenced (409 otherwise).
 * DELETE /api/admin/departments/{code}
 * @param {string} code Department code.
 * @returns {Promise<null>}
 */
export function deleteDepartment(code) {
  return apiClient.delete(`/api/admin/departments/${encodeURIComponent(code)}`);
}

/**
 * Assigns a department to a user, or removes the assignment when the
 * department is empty. The change is written to the user's Keycloak attribute.
 * PUT /api/admin/users/{userId}/department
 * @param {string} userId Keycloak user id.
 * @param {string|null} department Department code, or null/"" to remove.
 * @returns {Promise<null>}
 */
export function assignUserDepartment(userId, department) {
  return apiClient.put(`/api/admin/users/${encodeURIComponent(userId)}/department`, {
    department: department || null,
  });
}
