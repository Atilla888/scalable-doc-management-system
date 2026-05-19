package de.hof.dms.controller;

import de.hof.dms.config.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtDecoderConfig.class)
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOkWithoutToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithExpiredTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtDecoderConfig.EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithWrongIssuerTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtDecoderConfig.WRONG_ISSUER_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidBearerTokenReturns200() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtDecoderConfig.VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_username").value("viewer"));
    }
}
