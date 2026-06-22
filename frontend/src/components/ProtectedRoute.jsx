import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { isOAuthCallback } from "../auth/keycloakAuth";

/**
 * Wraps routes that require authentication.
 *
 * Props:
 *   requiredRole  – optional DMS role string (e.g. "dms_admin").
 *                   If provided, users lacking the role are sent to /unauthorized.
 *
 * Unauthenticated users are sent to /login (not keycloak.login() here — that
 * caused redirect loops while the OAuth callback was still being processed).
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
    return <Navigate to="/unauthorized" replace />;
  }

  return <Outlet />;
}
