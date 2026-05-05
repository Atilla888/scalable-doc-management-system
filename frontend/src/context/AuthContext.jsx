import { createContext, useContext, useEffect, useRef, useState } from 'react'
import keycloak from '../keycloak'

const AuthContext = createContext(null)

/**
 * Extracts DMS realm roles from the Keycloak token.
 * Keycloak puts realm roles under tokenParsed.realm_access.roles
 */
function extractRoles(kc) {
  return kc.tokenParsed?.realm_access?.roles ?? []
}

export function AuthProvider({ children }) {
  const [initialized, setInitialized] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [user, setUser] = useState(null)
  const [roles, setRoles] = useState([])
  const refreshIntervalRef = useRef(null)

  useEffect(() => {
    keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        pkceMethod: 'S256',
      })
      .then((isAuthenticated) => {
        setAuthenticated(isAuthenticated)
        if (isAuthenticated) {
          setUser(keycloak.tokenParsed)
          setRoles(extractRoles(keycloak))
          startTokenRefresh()
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
      startTokenRefresh()
    }

    keycloak.onAuthLogout = () => {
      setAuthenticated(false)
      setUser(null)
      setRoles([])
      stopTokenRefresh()
    }

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => keycloak.logout())
    }

    return () => stopTokenRefresh()
  }, [])

  function startTokenRefresh() {
    stopTokenRefresh()
    // Refresh the token every 30 seconds if it will expire within 60 seconds
    refreshIntervalRef.current = setInterval(() => {
      keycloak.updateToken(60).catch(() => keycloak.logout())
    }, 30_000)
  }

  function stopTokenRefresh() {
    if (refreshIntervalRef.current) {
      clearInterval(refreshIntervalRef.current)
      refreshIntervalRef.current = null
    }
  }

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

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
