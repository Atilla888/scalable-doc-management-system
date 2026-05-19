package de.hof.dms.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test {@link JwtDecoder} for auth security tests without a running Keycloak.
 * Recognizes fixed token strings; all other bearer tokens are rejected.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    public static final String VALID_TOKEN = "valid-test-token";
    public static final String EXPIRED_TOKEN = "expired-test-token";
    public static final String WRONG_ISSUER_TOKEN = "wrong-issuer-test-token";

    private static final String EXPECTED_ISSUER = "http://localhost:8080/realms/dms";

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> switch (token) {
            case VALID_TOKEN -> validJwt(EXPECTED_ISSUER);
            case EXPIRED_TOKEN -> throw new BadJwtException("Token expired");
            case WRONG_ISSUER_TOKEN -> throw new JwtValidationException(
                    "Invalid issuer",
                    List.of(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token",
                            "The iss claim is not valid",
                            null))
            );
            default -> throw new BadJwtException("Invalid bearer token");
        };
    }

    static Jwt validJwt(String issuer) {
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "none")
                .issuer(issuer)
                .subject("viewer-id")
                .claim("preferred_username", "viewer")
                .claim("realm_access", Map.of("roles", List.of("dms_viewer")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
