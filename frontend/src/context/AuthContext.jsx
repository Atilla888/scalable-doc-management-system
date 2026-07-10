/**
 * @module context/AuthContext
 * React context that owns the Keycloak session: initialization, token refresh,
 * and exposing the current user, roles, and auth actions to the app.
 */
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import keycloak from '../keycloak'
import { initKeycloak } from '../auth/keycloakAuth'

const AuthContext = createContext(null)

/**
 * Extracts DMS realm roles from the Keycloak token.
 * Keycloak puts realm roles under tokenParsed.realm_access.roles
 * @param {import('keycloak-js').default} kc Keycloak instance.
 * @returns {string[]} The realm role names (empty array if none).
 */
function extractRoles(kc) {
  return kc.tokenParsed?.realm_access?.roles ?? []
}

/**
 * Starts a 30s interval that refreshes the token when it nears expiry,
 * logging out on failure. Clears any existing interval first.
 * @param {{current: ?number}} intervalRef Ref holding the interval id.
 * @returns {void}
 */
function startTokenRefresh(intervalRef) {
  stopTokenRefresh(intervalRef)
  intervalRef.current = setInterval(() => {
    keycloak.updateToken(60).catch(() => keycloak.logout())
  }, 30_000)
}

/**
 * Stops and clears the token-refresh interval, if any.
 * @param {{current: ?number}} intervalRef Ref holding the interval id.
 * @returns {void}
 */
function stopTokenRefresh(intervalRef) {
  if (intervalRef.current) {
    clearInterval(intervalRef.current)
    intervalRef.current = null
  }
}

/**
 * Provider that initializes Keycloak, tracks auth state, and supplies the
 * {@link useAuth} context value to descendants.
 * @param {Object} props
 * @param {React.ReactNode} props.children Subtree that can consume auth state.
 * @returns {JSX.Element}
 */
export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [user, setUser] = useState(null)
  const [roles, setRoles] = useState([])
  const refreshIntervalRef = useRef(null)

  useEffect(() => {
    initKeycloak({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
      .then((isAuthenticated) => {
        setAuthenticated(isAuthenticated)
        if (isAuthenticated) {
          setUser(keycloak.tokenParsed)
          setRoles(extractRoles(keycloak))
          startTokenRefresh(refreshIntervalRef)
        }
        setInitialized(true)
      })
      .catch(() => {
        setInitialized(true)
      })

    keycloak.onAuthSuccess = () => {
      setAuthenticated(true)
      setUser(keycloak.tokenParsed)
      setRoles(extractRoles(keycloak))
      startTokenRefresh(refreshIntervalRef)
    }

    keycloak.onAuthLogout = () => {
      setAuthenticated(false)
      setUser(null)
      setRoles([])
      stopTokenRefresh(refreshIntervalRef)
    }

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => keycloak.logout())
    }

    return () => stopTokenRefresh(refreshIntervalRef)
  }, [])

  /**
   * Logs the user out and returns to /login.
   * @returns {void}
   */
  function logout() {
    keycloak.logout({ redirectUri: window.location.origin + '/login' })
  }

  const value = {
    keycloak,
    initialized,
    authenticated,
    user,
    roles,
    token: keycloak.token,
    hasRole: (role) => roles.includes(role),
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/**
 * Hook to access the auth context: `{ keycloak, initialized, authenticated,
 * user, roles, token, hasRole, logout }`.
 * @returns {Object} The auth context value.
 * @throws {Error} If used outside an {@link AuthProvider}.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
