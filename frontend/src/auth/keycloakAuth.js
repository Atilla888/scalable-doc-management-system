import keycloak from "../keycloak";

/** OAuth redirect callback is still in the URL — do not start another login. */
export function isOAuthCallback() {
  const params = new URLSearchParams(window.location.search);
  return params.has("code") || params.has("state") || params.has("error");
}

let initPromise = null;

/**
 * Initialize Keycloak once (React StrictMode would otherwise call init twice).
 */
export function initKeycloak(options) {
  if (!initPromise) {
    initPromise = keycloak.init(options);
  }
  return initPromise;
}

let loginRedirectInFlight = false;

/**
 * Redirect to Keycloak login at most once at a time.
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
 * Session is invalid for the API — clear Keycloak state instead of login() in a loop.
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
