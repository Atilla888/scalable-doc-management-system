/**
 * @module api/folders
 * Folder tree navigation and management API calls.
 */
import { apiClient } from "./client";

/**
 * Fetches the root folder view (folder plus its subfolders and documents).
 * GET /api/folders/root
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Object>} The root folder view.
 */
export function getRootFolder(signal) {
  return apiClient.get("/api/folders/root", { signal });
}

/**
 * Fetches a folder view by id (folder, breadcrumb, subfolders, documents).
 * GET /api/folders/:id
 * @param {string} id Folder id.
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Object>} The folder view.
 */
export function getFolder(id, signal) {
  return apiClient.get(`/api/folders/${encodeURIComponent(id)}`, { signal });
}

/**
 * Flat list of every folder the current user can read — used as a move target picker.
 * GET /api/folders/tree
 * @param {AbortSignal} [signal] Optional abort signal.
 * @returns {Promise<Array<Object>>} Flat array of folder descriptors.
 */
export function getFolderTree(signal) {
  return apiClient.get("/api/folders/tree", { signal });
}

/**
 * Creates a folder under `parentId`. The backend derives the path, sets the
 * ACL from the current user, and returns the new folder document.
 * POST /api/folders
 * @param {Object} params
 * @param {string} params.name New folder name.
 * @param {string} params.parentId Parent folder id.
 * @returns {Promise<Object>} The created folder.
 */
export function createFolder({ name, parentId }) {
  return apiClient.post("/api/folders", { name, parentId });
}

/**
 * Renames and/or moves a folder. Pass `name` to rename, `parentId` to move.
 * PATCH /api/folders/:id
 * @param {string} id Folder id.
 * @param {Object} params
 * @param {string} [params.name] New name.
 * @param {string} [params.parentId] New parent id.
 * @returns {Promise<Object>} The updated folder.
 */
export function updateFolder(id, { name, parentId }) {
  return apiClient.patch(`/api/folders/${encodeURIComponent(id)}`, { name, parentId });
}

/**
 * Deletes a folder. By default a non-empty folder is rejected (HTTP 409);
 * pass `recursive: true` to delete it together with its contents.
 * DELETE /api/folders/:id
 * @param {string} id Folder id.
 * @param {boolean} [recursive=false] Whether to delete contents too.
 * @returns {Promise<null>}
 */
export function deleteFolder(id, recursive = false) {
  return apiClient.delete(
    `/api/folders/${encodeURIComponent(id)}?recursive=${recursive ? "true" : "false"}`,
  );
}
