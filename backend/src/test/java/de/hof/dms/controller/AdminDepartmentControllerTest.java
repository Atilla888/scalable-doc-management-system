package de.hof.dms.controller;

import de.hof.dms.config.SecurityConfig;
import de.hof.dms.config.TestJwtDecoderConfig;
import de.hof.dms.dto.CreateDepartmentRequest;
import de.hof.dms.dto.DepartmentResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDepartmentController.class)
@Import({SecurityConfig.class, TestJwtDecoderConfig.class})
class AdminDepartmentControllerTest {

    private static final DepartmentResponse ITDLZ =
            new DepartmentResponse(
                    "dep-1",
                    "ITDLZ",
                    "IT-Dienstleistungszentrum",
                    true,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("dms_admin"));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor viewer() {
        return jwt().authorities(new SimpleGrantedAuthority("dms_viewer"));
    }

    @Test
    void anonymousUserIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/departments")).andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCannotListDepartments() throws Exception {
        mockMvc.perform(get("/api/admin/departments").with(viewer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotCreateUpdateDeleteOrAssign() throws Exception {
        mockMvc.perform(
                        post("/api/admin/departments")
                                .with(viewer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"FIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        put("/api/admin/departments/ITDLZ")
                                .with(viewer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/departments/ITDLZ").with(viewer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        put("/api/admin/users/user-1/department")
                                .with(viewer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"department\":\"ITDLZ\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsDepartments() throws Exception {
        when(departmentService.listDepartments()).thenReturn(List.of(ITDLZ));

        mockMvc.perform(get("/api/admin/departments").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ITDLZ"))
                .andExpect(jsonPath("$[0].displayName").value("IT-Dienstleistungszentrum"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void adminCreatesDepartment() throws Exception {
        when(departmentService.createDepartment(any(CreateDepartmentRequest.class))).thenReturn(ITDLZ);

        mockMvc.perform(
                        post("/api/admin/departments")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"itdlz\",\"displayName\":\"IT-Dienstleistungszentrum\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ITDLZ"));
    }

    @Test
    void duplicateDepartmentCodeIsRejectedWithConflict() throws Exception {
        when(departmentService.createDepartment(any(CreateDepartmentRequest.class)))
                .thenThrow(
                        new ApiException(
                                HttpStatus.CONFLICT, "A department with code 'ITDLZ' already exists"));

        mockMvc.perform(
                        post("/api/admin/departments")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"code\":\"ITDLZ\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A department with code 'ITDLZ' already exists"));
    }

    @Test
    void adminUpdatesDepartment() throws Exception {
        when(departmentService.updateDepartment(eq("ITDLZ"), any()))
                .thenReturn(
                        new DepartmentResponse(
                                "dep-1",
                                "ITDLZ",
                                "IT Service Center",
                                false,
                                ITDLZ.createdAt(),
                                Instant.parse("2026-02-01T00:00:00Z")));

        mockMvc.perform(
                        put("/api/admin/departments/ITDLZ")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"IT Service Center\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("IT Service Center"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminDeletesDepartment() throws Exception {
        mockMvc.perform(delete("/api/admin/departments/OLD").with(admin()))
                .andExpect(status().isNoContent());
        verify(departmentService).deleteDepartment("OLD");
    }

    @Test
    void deletingReferencedDepartmentReturnsConflict() throws Exception {
        doThrow(
                        new ApiException(
                                HttpStatus.CONFLICT,
                                "Department 'ITDLZ' cannot be deleted because users are still assigned"
                                        + " to it; deactivate it instead"))
                .when(departmentService)
                .deleteDepartment("ITDLZ");

        mockMvc.perform(delete("/api/admin/departments/ITDLZ").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void adminAssignsUserDepartment() throws Exception {
        mockMvc.perform(
                        put("/api/admin/users/user-1/department")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"department\":\"ITDLZ\"}"))
                .andExpect(status().isNoContent());
        verify(departmentService).assignUserDepartment(eq("user-1"), any());
    }

    @Test
    void assigningInactiveDepartmentReturnsConflict() throws Exception {
        doThrow(
                        new ApiException(
                                HttpStatus.CONFLICT,
                                "Department 'OLD' is inactive and cannot be assigned"))
                .when(departmentService)
                .assignUserDepartment(eq("user-1"), any());

        mockMvc.perform(
                        put("/api/admin/users/user-1/department")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"department\":\"OLD\"}"))
                .andExpect(status().isConflict());
    }
}
