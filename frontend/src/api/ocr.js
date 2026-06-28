import { apiClient } from "./client";

export function listOcrJobs(status, signal) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiClient.get(`/api/admin/ocr${query}`, { signal });
}

export function retryOcr(id) {
  return apiClient.post(`/api/admin/ocr/${encodeURIComponent(id)}/retry`);
}
