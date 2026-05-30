import { apiClient } from "./client";

export function getDocument(id, signal) {
  return apiClient.get(`/api/documents/${encodeURIComponent(id)}`, { signal });
}

export function uploadDocument(formData) {
  return apiClient.post("/api/documents", formData);
}

export function deleteDocument(id) {
  return apiClient.delete(`/api/documents/${encodeURIComponent(id)}`);
}

/**
 * Downloads the binary via fetch (so the Bearer token is attached) and triggers
 * a browser save. Throws ApiError on 403 so the caller can show a message.
 */
export async function downloadDocument(id, fileName) {
  const response = await apiClient.getRaw(`/api/documents/${encodeURIComponent(id)}/download`);
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName || "document";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
