package de.hof.dms.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Returns the currently authenticated user's identity and roles
 * extracted from the Keycloak JWT. Frontend calls GET /api/auth/me
 * to verify auth is working end-to-end.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthInfoController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        return ResponseEntity.ok(Map.of(
                "sub",               jwt.getSubject(),
                "preferred_username", jwt.getClaimAsString("preferred_username"),
                "email",             jwt.getClaimAsString("email"),
                "name",              jwt.getClaimAsString("name") != null
                                         ? jwt.getClaimAsString("name") : "",
                "realm_access",      realmAccess != null ? realmAccess : Map.of()
        ));
    }
}
