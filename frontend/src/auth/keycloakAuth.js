/**
 * @module auth/keycloakAuth
 * Keycloak initialization and login/logout redirect helpers, guarded so init
 * runs once and only one redirect is ever in flight at a time.
 */
import keycloak from "../keycloak";

/**
 * OAuth redirect callback is still in the URL — do not start another login.
 * @returns {boolean} True if the URL carries `code`, `state`, or `error` params.
 */
export function isOAuthCallback() {
  const params = new URLSearchParams(window.location.search);
  return params.has("code") || params.has("state") || params.has("error");
}

let initPromise = null;

/**
 * Initialize Keycloak once (React StrictMode would otherwise call init twice).
 * @param {Object} options Keycloak init options.
 * @returns {Promise<boolean>} Resolves to the authenticated state.
 */
export function initKeycloak(options) {
  if (!initPromise) {
    initPromise = keycloak.init(options);
  }
  return initPromise;
}

let loginRedirectInFlight = false;

/**
 * Redirect to Keycloak login at most once at a time. No-op if a redirect is
 * already in flight or the OAuth callback is still being processed.
 * @returns {void}
 */
export function redirectToLoginOnce() {
  if (loginRedirectInFlight || isOAuthCallback()) {
    return;
  }
  loginRedirectInFlight = true;
  keycloak
    .login({ redirectUri: window.location.origin + "/" })
    .catch(() => {})
    .finally(() => {
      loginRedirectInFlight = false;
    });
}

/**
 * Session is invalid for the API — clear Keycloak state instead of login() in a
 * loop, logging out and returning to /login.
 * @returns {void}
 */
export function clearSessionAndGoToLogin() {
  if (loginRedirectInFlight) {
    return;
  }
  loginRedirectInFlight = true;
  keycloak
    .logout({ redirectUri: window.location.origin + "/login" })
    .catch(() => {
      window.location.href = "/login";
    })
    .finally(() => {
      loginRedirectInFlight = false;
    });
}
