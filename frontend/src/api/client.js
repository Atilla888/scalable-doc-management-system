/**
 * @module api/client
 * Central authenticated fetch client: attaches the Keycloak bearer token,
 * normalizes error responses into {@link ApiError}, and exposes REST helpers.
 */
import keycloak from "../keycloak";
import { API_BASE_URL } from "../config";
import { clearSessionAndGoToLogin, redirectToLoginOnce } from "../auth/keycloakAuth";

/**
 * Error thrown for any non-2xx API response. Carries the HTTP status and the
 * backend's ProblemDetail `detail` message so the UI can react to 401/403/404.
 */
export class ApiError extends Error {
  constructor(status, detail, payload) {
    super(detail || `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
    this.payload = payload;
  }

  get isUnauthorized() {
    return this.status === 401;
  }

  get isForbidden() {
    return this.status === 403;
  }

  get isNotFound() {
    return this.status === 404;
  }
}

/**
 * Ensures a valid token and returns the Authorization header.
 * Refreshes the token if it expires within 30s; clears the session on failure.
 * @returns {Promise<Object>} Header object with the Bearer token (or empty).
 * @throws {ApiError} 401 when not signed in or the session expired.
 */
async function authHeader() {
  if (!keycloak.authenticated) {
    throw new ApiError(401, "Not signed in");
  }
  try {
    await keycloak.updateToken(30);
  } catch {
    clearSessionAndGoToLogin();
    throw new ApiError(401, "Session expired");
  }
  return keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {};
}

/**
 * Builds an {@link ApiError} from a failed Response, extracting the backend's
 * `detail`/`message`/`error` field or falling back to the status text.
 * @param {Response} response The failed fetch Response.
 * @returns {Promise<ApiError>}
 */
async function parseError(response) {
  let detail;
  let payload;
  try {
    payload = await response.json();
    detail = payload?.detail || payload?.message || payload?.error;
  } catch {
    detail = response.statusText;
  }
  return new ApiError(response.status, detail, payload);
}

/**
 * Performs an authenticated fetch and throws on non-2xx responses. Handles 401
 * by redirecting to login. JSON-encodes the body unless it is FormData.
 * @param {string} path Request path appended to the API base URL.
 * @param {Object} [options]
 * @param {string} [options.method="GET"] HTTP method.
 * @param {*} [options.body] Request body (FormData sent as-is, else JSON).
 * @param {Object} [options.headers] Additional request headers.
 * @param {AbortSignal} [options.signal] Signal to abort the request.
 * @returns {Promise<Response>} The raw fetch Response.
 * @throws {ApiError} On any non-ok response.
 */
async function request(path, { method = "GET", body, headers = {}, signal } = {}) {
  const auth = await authHeader();
  const isFormData = body instanceof FormData;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      ...auth,
      ...(body && !isFormData ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: isFormData ? body : body ? JSON.stringify(body) : undefined,
    signal,
  });

  if (response.status === 401) {
    if (keycloak.authenticated) {
      clearSessionAndGoToLogin();
    } else {
      redirectToLoginOnce();
    }
    throw await parseError(response);
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  return response;
}

/**
 * Authenticated REST client. Each method issues a request and parses the JSON
 * response (returning `null` for 204 No Content where applicable).
 */
export const apiClient = {
  async get(path, options) {
    const response = await request(path, { ...options, method: "GET" });
    return response.json();
  },
  async post(path, body, options) {
    const response = await request(path, { ...options, method: "POST", body });
    if (response.status === 204) return null;
    return response.json();
  },
  async patch(path, body, options) {
    const response = await request(path, { ...options, method: "PATCH", body });
    if (response.status === 204) return null;
    return response.json();
  },
  async delete(path, options) {
    const response = await request(path, { ...options, method: "DELETE" });
    return response.status === 204 ? null : response.json();
  },
  // Returns the raw Response so callers can read a binary blob (e.g. downloads).
  async getRaw(path, options) {
    return request(path, { ...options, method: "GET" });
  },
};
