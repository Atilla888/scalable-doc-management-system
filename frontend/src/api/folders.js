import { apiClient } from "./client";

export function getRootFolder(signal) {
  return apiClient.get("/api/folders/root", { signal });
}

export function getFolder(id, signal) {
  return apiClient.get(`/api/folders/${encodeURIComponent(id)}`, { signal });
}

/**
 * Creates a folder under `parentId`. The backend derives the path, sets the
 * ACL from the current user, and returns the new folder document.
 */
export function createFolder({ name, parentId }) {
  return apiClient.post("/api/folders", { name, parentId });
}
