/**
 * @module pages/Login
 * Login page that redirects authenticated users to the dashboard and otherwise
 * offers a "Sign in with Keycloak" action.
 */
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { KEYCLOAK_REALM, KEYCLOAK_URL, SHOW_DEMO_USERS } from '../config'

/**
 * Renders the login screen.
 * @returns {JSX.Element}
 */
const Login = () => {
  const { initialized, authenticated, keycloak } = useAuth()

  if (!initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-text-secondary text-sm">
        Initializing…
      </div>
    )
  }

  if (authenticated) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 text-text">
      <div className="grid w-full max-w-5xl gap-0 overflow-hidden rounded-3xl border border-border bg-surface shadow-sm lg:grid-cols-2">
        <div className="relative flex min-h-[520px] flex-col justify-between overflow-hidden bg-gradient-to-br from-primary via-secondary to-accent p-8 text-white">
          <div>
            <p className="text-sm font-medium uppercase tracking-[0.25em] text-white/80">Central DMS</p>
            <h1 className="mt-4 max-w-md text-4xl font-semibold leading-tight">
              Permission-safe document management for public authorities
            </h1>
            <p className="mt-4 max-w-md text-sm text-white/85">
              Authenticate with your organizational account through Keycloak. Your access scope is determined by your assigned roles.
            </p>
          </div>

          <div className="grid gap-3 text-sm text-white/90 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">Keycloak</p>
              <p className="mt-1 text-white/80">SSO authentication</p>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">RBAC</p>
              <p className="mt-1 text-white/80">Backend enforced</p>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">Search</p>
              <p className="mt-1 text-white/80">Permission-safe</p>
            </div>
          </div>
        </div>

        <div className="flex items-center p-8">
          <div className="w-full space-y-6">
            <div>
              <p className="text-sm font-medium text-text-secondary">Sign in</p>
              <h2 className="mt-2 text-3xl font-semibold text-text">Continue with Keycloak</h2>
              <p className="mt-3 text-sm text-text-secondary">
                You will be redirected to the Keycloak login page. Enter your credentials there and you will be brought back automatically.
              </p>
            </div>

            <button
              onClick={() => keycloak.login()}
              className="flex w-full items-center justify-center gap-3 rounded-2xl border border-border bg-primary px-5 py-4 text-sm font-medium text-white shadow-sm hover:bg-primary/90 transition"
            >
              <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-white/15 text-base font-semibold">
                K
              </span>
              Sign in with Keycloak
            </button>

            <div className={`grid gap-4 ${SHOW_DEMO_USERS ? 'sm:grid-cols-2' : ''}`}>
              {SHOW_DEMO_USERS && (
                <div className="rounded-2xl border border-border bg-background p-4">
                  <p className="text-sm font-semibold text-text">Demo users</p>
                  <ul className="mt-2 space-y-1 text-xs text-text-secondary">
                    <li>admin@dms.local</li>
                    <li>manager@dms.local</li>
                    <li>contributor@dms.local</li>
                    <li>viewer@dms.local</li>
                    <li className="pt-1 text-text-secondary/80">Password: changeme_dev (dev only)</li>
                  </ul>
                </div>
              )}
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-sm font-semibold text-text">Keycloak</p>
                <p className="mt-2 text-xs text-text-secondary">
                  Running at{' '}
                  <a href={KEYCLOAK_URL} target="_blank" rel="noopener noreferrer" className="text-primary underline">
                    {KEYCLOAK_URL}
                  </a>
                  <br />
                  Realm: <span className="font-mono">{KEYCLOAK_REALM}</span>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Login
