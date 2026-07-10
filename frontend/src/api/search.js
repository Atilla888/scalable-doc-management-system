/**
 * @module api/search
 * Permission-safe document search API call.
 */
import { apiClient } from "./client";

/**
 * Searches documents with optional filters, sorting, and pagination.
 * Blank/undefined params are omitted from the query string.
 * GET /api/search
 * @param {Object} [params] Search criteria.
 * @param {string} [params.query] Free-text query.
 * @param {string} [params.type] Document type filter.
 * @param {string} [params.department] Department filter.
 * @param {string} [params.folder] Folder id filter.
 * @param {string} [params.status] OCR status filter.
 * @param {string} [params.dateFrom] Inclusive start date.
 * @param {string} [params.dateTo] Inclusive end date.
 * @param {string} [params.sort] Sort key (e.g. "date_desc").
 * @param {number} [params.page=0] Zero-based page index.
 * @param {number} [params.limit=20] Page size.
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Object>} Paged response `{ content, page, limit, totalElements, totalPages, hasMore }`.
 */
export function searchDocuments(params = {}, signal) {
  const sp = new URLSearchParams();
  const set = (key, value) => {
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      sp.set(key, value);
    }
  };
  set("query", params.query);
  set("type", params.type);
  set("department", params.department);
  set("folder", params.folder);
  set("status", params.status);
  set("dateFrom", params.dateFrom);
  set("dateTo", params.dateTo);
  set("sort", params.sort);
  sp.set("page", params.page ?? 0);
  sp.set("limit", params.limit ?? 20);
  return apiClient.get(`/api/search?${sp.toString()}`, { signal });
}
