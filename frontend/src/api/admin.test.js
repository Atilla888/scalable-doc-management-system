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

import {
  assignUserDepartment,
  createDepartment,
  deleteDepartment,
  listDepartments,
  updateDepartment,
} from "./admin";

/**
 * Stubs fetch with one canned response and returns the mock.
 * @param {number} status HTTP status to answer with.
 * @param {*} [body] JSON body (ignored for 204).
 */
function stubFetch(status = 200, body = null) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("admin department API", () => {
  beforeEach(() => {
    keycloakMock.authenticated = true;
    keycloakMock.updateToken.mockResolvedValue(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lists departments from the admin endpoint", async () => {
    const fetchMock = stubFetch(200, [{ code: "ITDLZ", active: true }]);

    const departments = await listDepartments();

    expect(departments).toEqual([{ code: "ITDLZ", active: true }]);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/admin/departments");
    expect(options.method).toBe("GET");
  });

  it("creates a department with a JSON payload", async () => {
    const fetchMock = stubFetch(201, { code: "FIN" });

    await createDepartment({ code: "fin", displayName: "Finance" });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/admin/departments");
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({ code: "fin", displayName: "Finance" });
  });

  it("updates a department via PUT with an encoded code", async () => {
    const fetchMock = stubFetch(200, { code: "ITDLZ", active: false });

    await updateDepartment("ITDLZ", { active: false });

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/admin/departments/ITDLZ");
    expect(options.method).toBe("PUT");
    expect(JSON.parse(options.body)).toEqual({ active: false });
  });

  it("deletes a department and resolves null on 204", async () => {
    const fetchMock = stubFetch(204);

    await expect(deleteDepartment("OLD")).resolves.toBeNull();

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/admin/departments/OLD");
    expect(options.method).toBe("DELETE");
  });

  it("assigns a user department via PUT and maps empty to null", async () => {
    const fetchMock = stubFetch(204);

    await assignUserDepartment("user-1", "");

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe("http://api.test/api/admin/users/user-1/department");
    expect(options.method).toBe("PUT");
    expect(JSON.parse(options.body)).toEqual({ department: null });
  });

  it("surfaces the backend conflict detail when deletion is rejected", async () => {
    stubFetch(409, { detail: "Department 'ITDLZ' cannot be deleted" });

    await expect(deleteDepartment("ITDLZ")).rejects.toMatchObject({
      status: 409,
      detail: "Department 'ITDLZ' cannot be deleted",
    });
  });

  it("surfaces the backend detail when Keycloak refuses an assignment", async () => {
    stubFetch(502, {
      detail: "Keycloak refused to update the user in Keycloak [HTTP 400, error-user-attribute-required (field: email)]",
    });

    await expect(assignUserDepartment("user-1", "ITDLZ")).rejects.toMatchObject({
      status: 502,
      detail: expect.stringContaining("error-user-attribute-required"),
    });
  });
});
