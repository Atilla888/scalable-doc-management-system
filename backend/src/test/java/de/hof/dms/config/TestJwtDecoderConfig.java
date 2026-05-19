package de.hof.dms.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

/**
 * Avoids JWKS HTTP calls to Keycloak during {@code @SpringBootTest} startup.
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#jwt()}
 * supplies authentication directly in security tests.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return mock(JwtDecoder.class);
    }
}
