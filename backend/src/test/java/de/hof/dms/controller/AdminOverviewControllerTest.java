package de.hof.dms.controller;

import de.hof.dms.config.SecurityConfig;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.dto.AdminOverviewResponse;
import de.hof.dms.service.AdminOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOverviewController.class)
@Import({SecurityConfig.class, TestJwtDecoderConfig.class})
class AdminOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOverviewService adminOverviewService;

    @Test
    void anonymousUserIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminUserIsForbidden() throws Exception {
        mockMvc.perform(
                        get("/api/admin/overview")
                                .with(
                                        jwt()
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "dms_viewer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReceivesOverview() throws Exception {
        when(adminOverviewService.getOverview())
                .thenReturn(
                        new AdminOverviewResponse(
                                List.of(
                                        new AdminOverviewResponse.UserSummary(
                                                "1",
                                                "admin",
                                                "Admin User",
                                                "admin@example.test",
                                                null,
                                                List.of("dms_admin"),
                                                true)),
                                List.of(),
                                List.of(),
                                List.of(
                                        new AdminOverviewResponse.ComponentHealth(
                                                "Backend API", "UP", "Ready")),
                                new AdminOverviewResponse.SystemMetrics(1, 2, 3)));

        SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin =
                jwt().authorities(new SimpleGrantedAuthority("dms_admin"));

        mockMvc.perform(get("/api/admin/overview").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].username").value("admin"))
                .andExpect(jsonPath("$.health[0].status").value("UP"))
                .andExpect(jsonPath("$.metrics.documents").value(2));
    }
}
