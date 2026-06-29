package de.hof.dms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminServiceTest {

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
