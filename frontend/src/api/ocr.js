/**
 * @module api/ocr
 * OCR job queue API calls (admin).
 */
import { apiClient } from "./client";

/**
 * Lists OCR jobs, optionally filtered by status.
 * GET /api/admin/ocr[?status=...]
 * @param {string} [status] Optional status filter.
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Array<Object>>} The OCR jobs.
 */
export function listOcrJobs(status, signal) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiClient.get(`/api/admin/ocr${query}`, { signal });
}

/**
 * Requeues a failed OCR job for another attempt.
 * POST /api/admin/ocr/:id/retry
 * @param {string} id OCR job / document id.
 * @returns {Promise<Object|null>}
 */
export function retryOcr(id) {
  return apiClient.post(`/api/admin/ocr/${encodeURIComponent(id)}/retry`);
}
