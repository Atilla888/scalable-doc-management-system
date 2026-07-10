/**
 * @module config
 * Central place for environment-driven configuration. All values come from Vite
 * env vars (VITE_*) with sensible localhost defaults so nothing is hardcoded per
 * environment.
 */

// Central place for environment-driven configuration.
// All values come from Vite env vars (VITE_*) so nothing is hardcoded per environment.

/** Base URL of the DMS backend API (trailing slash stripped). */
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") || "http://localhost:8081";

/** Base URL of the Keycloak server. */
export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8080";
/** Keycloak realm name. */
export const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || "dms";
/** Keycloak client id for this frontend. */
export const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "dms-frontend";
/** Whether to show the demo-users hint on the login page (dev only). */
export const SHOW_DEMO_USERS = import.meta.env.VITE_SHOW_DEMO_USERS === "true";
