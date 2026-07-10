/**
 * @module components/ProtectedRoute
 * Route guard that gates child routes on authentication and (optionally) a role.
 */
import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { isOAuthCallback } from "../auth/keycloakAuth";

/**
 * Wraps routes that require authentication. Shows an initializing/completing
 * state while auth resolves, redirects unauthenticated users to /login (not
 * keycloak.login() here — that caused redirect loops while the OAuth callback
 * was still being processed), and renders the child <Outlet> when allowed.
 * @param {Object} props
 * @param {string} [props.requiredRole] Optional DMS role (e.g. "dms_admin");
 *   users lacking it are sent to /unauthorized.
 * @returns {JSX.Element}
 */
export default function ProtectedRoute({ requiredRole }) {
  const { initialized, authenticated, hasRole } = useAuth();

  if (!initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-text-secondary text-sm">
        Initializing…
      </div>
    );
  }

  if (!authenticated) {
    if (isOAuthCallback()) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-background text-text-secondary text-sm">
          Completing sign in…
        </div>
      );
    }
    return <Navigate to="/login" replace />;
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <Navigate to="/unauthorized" replace state={{ requiredRole }} />;
  }

  return <Outlet />;
}
