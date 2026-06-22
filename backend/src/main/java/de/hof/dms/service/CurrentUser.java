package de.hof.dms.service;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CurrentUser(
        String subject,
        String username,
        String email,
        String department,
        List<String> roles) {

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
