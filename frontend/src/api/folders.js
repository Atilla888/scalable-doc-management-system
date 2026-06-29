import { apiClient } from "./client";

export function getRootFolder(signal) {
  return apiClient.get("/api/folders/root", { signal });
}

export function getFolder(id, signal) {
  return apiClient.get(`/api/folders/${encodeURIComponent(id)}`, { signal });
}

/** Flat list of every folder the current user can read — used as a move target picker. */
export function getFolderTree(signal) {
  return apiClient.get("/api/folders/tree", { signal });
}

/**
 * Creates a folder under `parentId`. The backend derives the path, sets the
 * ACL from the current user, and returns the new folder document.
 */
export function createFolder({ name, parentId }) {
  return apiClient.post("/api/folders", { name, parentId });
}

/** Renames and/or moves a folder. Pass `name` to rename, `parentId` to move. */
export function updateFolder(id, { name, parentId }) {
  return apiClient.patch(`/api/folders/${encodeURIComponent(id)}`, { name, parentId });
}

/**
 * Deletes a folder. By default a non-empty folder is rejected (HTTP 409);
 * pass `recursive: true` to delete it together with its contents.
 */
export function deleteFolder(id, recursive = false) {
  return apiClient.delete(
    `/api/folders/${encodeURIComponent(id)}?recursive=${recursive ? "true" : "false"}`,
  );
}
