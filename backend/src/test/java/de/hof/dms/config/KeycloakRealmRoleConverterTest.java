package de.hof.dms.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final SecurityConfig.KeycloakRealmRoleConverter converter =
            new SecurityConfig.KeycloakRealmRoleConverter();

    @Test
    void convertsRealmRolesFromJwt() {
        Jwt jwt = jwtWithRoles("dms_viewer", "dms_contributor");

        var authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(a -> ((SimpleGrantedAuthority) a).getAuthority())
                .containsExactlyInAnyOrder("dms_viewer", "dms_contributor");
    }

    @Test
    void returnsEmptyWhenRealmAccessMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwtWithRoles(String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
