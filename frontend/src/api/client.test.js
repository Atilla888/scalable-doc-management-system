import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

const keycloakMock = vi.hoisted(() => ({
  authenticated: true,
  token: "tok123",
  updateToken: vi.fn().mockResolvedValue(true),
}));
vi.mock("../keycloak", () => ({ default: keycloakMock }));
vi.mock("../config", () => ({ API_BASE_URL: "http://api.test" }));
vi.mock("../auth/keycloakAuth", () => ({
  clearSessionAndGoToLogin: vi.fn(),
  redirectToLoginOnce: vi.fn(),
}));

import { apiClient, ApiError } from "./client";

describe("ApiError", () => {
  it("classifies HTTP status codes the UI reacts to", () => {
    expect(new ApiError(401, "x").isUnauthorized).toBe(true);
    expect(new ApiError(403, "x").isForbidden).toBe(true);
    expect(new ApiError(404, "x").isNotFound).toBe(true);
    expect(new ApiError(500, "x").isUnauthorized).toBe(false);
  });
});

describe("apiClient", () => {
  beforeEach(() => {
    keycloakMock.authenticated = true;
    keycloakMock.token = "tok123";
    keycloakMock.updateToken.mockResolvedValue(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("attaches the Keycloak bearer token to requests", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ok: true }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await apiClient.get("/api/folders/root");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/folders/root");
    expect(options.headers.Authorization).toBe("Bearer tok123");
  });

  it("throws a 401 ApiError before calling fetch when not signed in", async () => {
    keycloakMock.authenticated = false;
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiClient.get("/api/folders/root")).rejects.toMatchObject({
      status: 401,
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("surfaces the backend detail message on a 403", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 403,
      json: async () => ({ detail: "Access denied" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiClient.get("/api/documents/1")).rejects.toMatchObject({
      status: 403,
      detail: "Access denied",
    });
  });
});
