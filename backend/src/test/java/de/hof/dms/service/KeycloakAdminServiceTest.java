package de.hof.dms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.exception.ApiException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminServiceTest {

    private static final String TOKEN_RESPONSE = "{\"access_token\":\"admin-token\"}";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KeycloakAdminService service;

    private void createService() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service =
                new KeycloakAdminService(
                        builder,
                        new ObjectMapper(),
                        "http://keycloak:8080",
                        "dms",
                        "dms-admin-api",
                        "test-secret");
        server.expect(
                        once(),
                        requestTo(
                                "http://keycloak:8080/realms/dms/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
    }

    @Test
    void updateUserDepartmentSetsTheAttributeAndPreservesOthers() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess(MANAGER_REPRESENTATION, MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(jsonPath("$.attributes.department[0]").value("ITDLZ"))
                .andExpect(jsonPath("$.attributes.locale[0]").value("de"))
                // The FULL representation must be echoed unchanged: Keycloak
                // validates the whole user profile against the submitted body, so
                // omitting built-in fields fails with error-user-attribute-required,
                // and altering them would rename or disable the user.
                .andExpect(jsonPath("$.username").value("manager@dms.local"))
                .andExpect(jsonPath("$.email").value("manager@dms.local"))
                .andExpect(jsonPath("$.firstName").value("Department"))
                .andExpect(jsonPath("$.lastName").value("Manager"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.updateUserDepartment("user-1", "ITDLZ")).isTrue();
        server.verify();
    }

    private static final String MANAGER_REPRESENTATION =
            """
            {
              "id":"user-1",
              "username":"manager@dms.local",
              "email":"manager@dms.local",
              "firstName":"Department",
              "lastName":"Manager",
              "enabled":true,
              "attributes":{"department":["OLD"],"locale":["de"]}
            }
            """;

    @Test
    void updateUserDepartmentRemovesTheAttributeForNull() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(MANAGER_REPRESENTATION, MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.attributes.department").doesNotExist())
                .andExpect(jsonPath("$.attributes.locale[0]").value("de"))
                .andExpect(jsonPath("$.username").value("manager@dms.local"))
                .andExpect(jsonPath("$.email").value("manager@dms.local"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(service.updateUserDepartment("user-1", null)).isTrue();
        server.verify();
    }

    @Test
    void keycloakValidationRejectionBecomesSanitizedBadGateway() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(MANAGER_REPRESENTATION, MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"field\":\"email\",\"errorMessage\":"
                                                + "\"error-user-attribute-required\",\"params\":[\"email\"]}"));

        assertThatThrownBy(() -> service.updateUserDepartment("user-1", "ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
                .hasMessageContaining("error-user-attribute-required")
                .hasMessageContaining("email")
                .hasMessageNotContaining("Bearer")
                .hasMessageNotContaining("admin-token");
        server.verify();
    }

    @Test
    void keycloakForbiddenBecomesSanitizedBadGateway() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(MANAGER_REPRESENTATION, MediaType.APPLICATION_JSON));
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(
                        withStatus(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"unknown_error\"}"));

        assertThatThrownBy(() -> service.updateUserDepartment("user-1", "ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
                .hasMessageContaining("HTTP 403");
        server.verify();
    }

    @Test
    void keycloakServerErrorOnLoadBecomesSanitizedBadGateway() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/user-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> service.updateUserDepartment("user-1", "ITDLZ"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY))
                .hasMessageContaining("HTTP 500");
        server.verify();
    }

    @Test
    void updateUserDepartmentReturnsFalseForUnknownUser() {
        createService();
        server.expect(
                        once(),
                        requestTo("http://keycloak:8080/admin/realms/dms/users/ghost"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(service.updateUserDepartment("ghost", "ITDLZ")).isFalse();
        server.verify();
    }

    @Test
    void hasUsersWithDepartmentQueriesByAttribute() {
        createService();
        server.expect(
                        once(),
                        requestTo(
                                Matchers.allOf(
                                        Matchers.startsWith(
                                                "http://keycloak:8080/admin/realms/dms/users?q=department"),
                                        Matchers.containsString("ITDLZ"),
                                        Matchers.containsString("max=1"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"user-1\"}]", MediaType.APPLICATION_JSON));

        assertThat(service.hasUsersWithDepartment("ITDLZ")).isTrue();
        server.verify();
    }

    @Test
    void hasUsersWithDepartmentReturnsFalseWhenNoneMatch() {
        createService();
        server.expect(
                        once(),
                        requestTo(
                                Matchers.startsWith(
                                        "http://keycloak:8080/admin/realms/dms/users?q=department")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.hasUsersWithDepartment("OLD")).isFalse();
        server.verify();
    }

    @Test
    void loadsRealUsersRolesAndDepartmentsFromKeycloak() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminService service =
                new KeycloakAdminService(
                        builder,
                        new ObjectMapper(),
                        "http://keycloak:8080",
                        "dms",
                        "dms-admin-api",
                        "test-secret");

        server.expect(
                        once(),
                        requestTo(
                                "http://keycloak:8080/realms/dms/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"admin-token\"}",
                                MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://keycloak:8080/admin/realms/dms/roles"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(
                        withSuccess(
                                """
                                [
                                  {"name":"offline_access"},
                                  {"name":"dms_viewer","description":"Read-only access"},
                                  {"name":"dms_admin","description":"Full access"}
                                ]
                                """,
                                MediaType.APPLICATION_JSON));

        server.expect(
                        once(),
                        requestTo(
                                "http://keycloak:8080/admin/realms/dms/users?max=500&briefRepresentation=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                [
                                  {
                                    "id":"user-1",
                                    "username":"viewer",
                                    "firstName":"Document",
                                    "lastName":"Viewer",
                                    "email":"viewer@dms.local",
                                    "enabled":true,
                                    "attributes":{"department":["ITDLZ"]}
                                  },
                                  {
                                    "id":"service-1",
                                    "username":"service-account-dms-admin-api",
                                    "enabled":true,
                                    "serviceAccountClientId":"dms-admin-api"
                                  }
                                ]
                                """,
                                MediaType.APPLICATION_JSON));

        server.expect(
                        once(),
                        requestTo(
                                "http://keycloak:8080/admin/realms/dms/users/user-1/role-mappings/realm/composite"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                [
                                  {"name":"default-roles-dms"},
                                  {"name":"dms_viewer"}
                                ]
                                """,
                                MediaType.APPLICATION_JSON));

        KeycloakAdminService.DirectorySnapshot result = service.loadDirectory();

        assertThat(result.users()).hasSize(1);
        assertThat(result.users().getFirst().displayName()).isEqualTo("Document Viewer");
        assertThat(result.users().getFirst().department()).isEqualTo("ITDLZ");
        assertThat(result.users().getFirst().roles()).containsExactly("dms_viewer");
        assertThat(result.roles()).extracting("name").containsExactly("dms_admin", "dms_viewer");
        server.verify();
    }
}
