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
