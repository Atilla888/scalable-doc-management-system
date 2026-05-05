import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Wraps routes that require authentication.
 *
 * Props:
 *   requiredRole  – optional DMS role string (e.g. "DMS_ADMIN").
 *                   If provided, users lacking the role are sent to /unauthorized.
 */
export default function ProtectedRoute({ requiredRole }) {
  const { initialized, authenticated, hasRole } = useAuth()

  if (!initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-text-secondary text-sm">
        Initializing…
      </div>
    )
  }

  if (!authenticated) {
    return <Navigate to="/login" replace />
  }

  if (requiredRole && !hasRole(requiredRole)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <Outlet />
}
