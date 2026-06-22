package de.hof.dms.controller;

import de.hof.dms.config.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
      "spring.profiles.active=no-mongo",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
    })
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class AuthInfoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meReturnsRolesFromJwt() throws Exception {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer("http://localhost:8080/realms/dms")
                .subject("viewer-id")
                .claim("preferred_username", "viewer")
                .claim("email", "viewer@dms.local")
                .claim("realm_access", Map.of("roles", List.of("dms_viewer")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc.perform(get("/api/auth/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_username").value("viewer"))
                .andExpect(jsonPath("$.realm_access.roles").isArray())
                .andExpect(jsonPath("$.realm_access.roles", hasItem("dms_viewer")));
    }

    @Test
    void adminEndpointRequiresDmsAdminRole() throws Exception {
        var viewerJwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer("http://localhost:8080/realms/dms")
                .subject("viewer-id")
                .claim("realm_access", Map.of("roles", List.of("dms_viewer")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc.perform(get("/api/admin/health")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(viewerJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsDmsAdmin() throws Exception {
        var adminJwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer("http://localhost:8080/realms/dms")
                .subject("admin-id")
                .claim("realm_access", Map.of("roles", List.of("dms_admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        mockMvc.perform(get("/api/admin/health")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(adminJwt)
                                .authorities(new SimpleGrantedAuthority("dms_admin"))))
                .andExpect(status().isNotFound());
    }
}
