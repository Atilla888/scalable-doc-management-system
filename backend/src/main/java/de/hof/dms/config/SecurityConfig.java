package de.hof.dms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central Spring Security configuration for the DMS backend.
 *
 * <p>Configures the application as a stateless OAuth2 resource server that validates
 * Keycloak-issued JWTs, wires CORS for the frontend, and enforces coarse-grained URL
 * authorization: health/actuator endpoints are public, {@code /api/admin/**} requires
 * the {@code dms_admin} realm role, and all other {@code /api/**} endpoints require an
 * authenticated user. Method-level security is enabled for finer-grained checks.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${dms.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    /**
     * Builds the application's security filter chain: stateless sessions, CORS enabled,
     * CSRF disabled, URL-based role authorization, and JWT-based OAuth2 resource server
     * validation using the Keycloak realm-role converter.
     *
     * @param http the HttpSecurity builder provided by Spring
     * @return the configured SecurityFilterChain
     * @throws Exception if the security configuration cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Health, publicly accessible for probes
                .requestMatchers("/health", "/actuator/health", "/actuator/info").permitAll()
                // OpenAPI spec + Swagger UI — publicly readable API documentation
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Admin endpoints, dms_admin only
                .requestMatchers("/api/admin/**").hasAuthority("dms_admin")
                // All other API endpoints, any authenticated user
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Maps Keycloak realm roles (from realm_access.roles in the JWT)
     * to Spring Security GrantedAuthority objects.
     *
     * Keycloak token structure:
     *   "realm_access": { "roles": ["dms_admin", "dms_viewer", ...] }
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    /**
     * Converts the {@code realm_access.roles} array of a Keycloak JWT into Spring Security
     * {@link GrantedAuthority} instances (one {@link SimpleGrantedAuthority} per role name).
     */
    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        /**
         * Extracts realm roles from the token, returning an empty list when the
         * {@code realm_access} claim or its {@code roles} entry is absent.
         *
         * @param jwt the validated Keycloak JWT
         * @return the granted authorities derived from the token's realm roles
         */
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            // Fail closed: a missing or malformed realm_access/roles claim yields no
            // authorities rather than a ClassCastException 500. An unchecked cast here
            // would blow up on any token whose roles claim is not a JSON array.
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(String::valueOf)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Defines the CORS policy applied to {@code /api/**}: origins are taken from the
     * {@code dms.cors.allowed-origins} property, and credentialed requests using the
     * standard REST verbs and auth/content headers are permitted.
     *
     * @return the CORS configuration source registered for the API paths
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
