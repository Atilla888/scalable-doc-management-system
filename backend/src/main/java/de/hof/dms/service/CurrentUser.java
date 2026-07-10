package de.hof.dms.service;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the authenticated caller extracted from the OIDC access
 * token, exposing the identity and realm roles that the RBAC and service layers
 * use to make authorization decisions.
 *
 * @param subject the token subject (stable Keycloak user id)
 * @param username the preferred username, or the subject when none is present
 * @param email the caller's email address, if present in the token
 * @param department the caller's department claim, if present
 * @param roles the realm roles flattened from {@code realm_access}
 */
public record CurrentUser(
        String subject,
        String username,
        String email,
        String department,
        List<String> roles) {

    /**
     * Builds a {@code CurrentUser} from a Keycloak JWT, falling back to the
     * subject when no {@code preferred_username} claim is present and flattening
     * the realm roles from {@code realm_access}.
     */
    public static CurrentUser fromJwt(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        String department = jwt.getClaimAsString("department");
        List<String> roles = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roleList) {
            for (Object role : roleList) {
                roles.add(String.valueOf(role));
            }
        }
        return new CurrentUser(
                jwt.getSubject(),
                username,
                jwt.getClaimAsString("email"),
                department,
                roles);
    }
}
