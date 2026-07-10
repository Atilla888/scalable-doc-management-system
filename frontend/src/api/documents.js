/**
 * @module api/documents
 * Document CRUD, upload, and download API calls.
 */
import { apiClient } from "./client";

/**
 * Fetches a single document's metadata.
 * GET /api/documents/:id
 * @param {string} id Document id.
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Object>} The document metadata.
 */
export function getDocument(id, signal) {
  return apiClient.get(`/api/documents/${encodeURIComponent(id)}`, { signal });
}

/**
 * Uploads a new document (file plus metadata).
 * POST /api/documents
 * @param {FormData} formData Multipart body containing the file and fields.
 * @returns {Promise<Object>} The created document.
 */
export function uploadDocument(formData) {
  return apiClient.post("/api/documents", formData);
}

/**
 * Deletes a document.
 * DELETE /api/documents/:id
 * @param {string} id Document id.
 * @returns {Promise<null>}
 */
export function deleteDocument(id) {
  return apiClient.delete(`/api/documents/${encodeURIComponent(id)}`);
}

/**
 * Downloads the binary via fetch (so the Bearer token is attached) and triggers
 * a browser save. Throws ApiError on 403 so the caller can show a message.
 * GET /api/documents/:id/download
 * @param {string} id Document id.
 * @param {string} [fileName] Suggested save filename.
 * @returns {Promise<void>}
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
