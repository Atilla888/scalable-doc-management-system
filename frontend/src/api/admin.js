import { apiClient } from "./client";

export function getAdminOverview(signal) {
  return apiClient.get("/api/admin/overview", { signal });
}
