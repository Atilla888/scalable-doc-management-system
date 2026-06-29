import { apiClient } from "./client";

/**
 * Searches documents with optional filters, sorting, and pagination.
 * Returns the paged response `{ content, page, limit, totalElements, totalPages, hasMore }`.
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
