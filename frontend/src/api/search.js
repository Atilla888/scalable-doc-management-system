import { apiClient } from "./client";

export function searchDocuments(query, signal) {
  return apiClient.get(`/api/search?q=${encodeURIComponent(query)}`, { signal });
}
