import { apiClient } from "./client";

export function getRootFolder(signal) {
  return apiClient.get("/api/folders/root", { signal });
}

export function getFolder(id, signal) {
  return apiClient.get(`/api/folders/${encodeURIComponent(id)}`, { signal });
}
