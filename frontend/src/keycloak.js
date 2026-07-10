/**
 * @module keycloak
 * Singleton Keycloak client instance configured from {@link module:config}.
 * Shared across the app so a single auth session is maintained.
 */
import Keycloak from 'keycloak-js'
import { KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM, KEYCLOAK_URL } from './config'

const keycloak = new Keycloak({
  url:      KEYCLOAK_URL,
  realm:    KEYCLOAK_REALM,
  clientId: KEYCLOAK_CLIENT_ID,
})

export default keycloak
