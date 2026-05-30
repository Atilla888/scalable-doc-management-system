import { createContext, useContext, useEffect, useRef, useState } from 'react'
import keycloak from '../keycloak'
import { initKeycloak } from '../auth/keycloakAuth'

const AuthContext = createContext(null)

/**
 * Extracts DMS realm roles from the Keycloak token.
 * Keycloak puts realm roles under tokenParsed.realm_access.roles
 */
function extractRoles(kc) {
  return kc.tokenParsed?.realm_access?.roles ?? []
}

function startTokenRefresh(intervalRef) {
  stopTokenRefresh(intervalRef)
  intervalRef.current = setInterval(() => {
    keycloak.updateToken(60).catch(() => keycloak.logout())
  }, 30_000)
}

function stopTokenRefresh(intervalRef) {
  if (intervalRef.current) {
    clearInterval(intervalRef.current)
    intervalRef.current = null
  }
}

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

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
