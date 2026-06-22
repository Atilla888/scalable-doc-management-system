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
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("sub", jwt.getSubject());
        body.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        body.put("email", jwt.getClaimAsString("email"));
        body.put("name", jwt.getClaimAsString("name") != null ? jwt.getClaimAsString("name") : "");
        body.put("realm_access", realmAccess != null ? realmAccess : Map.of());
        if (jwt.hasClaim("department")) {
            body.put("department", jwt.getClaimAsString("department"));
        }
        return ResponseEntity.ok(body);
    }
}
