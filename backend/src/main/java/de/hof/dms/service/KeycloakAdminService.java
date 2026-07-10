package de.hof.dms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hof.dms.dto.AdminOverviewResponse.RoleSummary;
import de.hof.dms.dto.AdminOverviewResponse.UserSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only gateway to the Keycloak Admin REST API. Authenticates with the
 * client-credentials grant and loads the realm's DMS users and {@code dms_}
 * roles for the administrator overview. Only DMS-scoped roles and non-service
 * accounts are surfaced.
 */
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${dms.keycloak.admin.base-url:http://localhost:8080}") String baseUrl,
            @Value("${dms.keycloak.admin.realm:dms}") String realm,
            @Value("${dms.keycloak.admin.client-id:dms-admin-api}") String clientId,
            @Value("${dms.keycloak.admin.client-secret:}") String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Loads the realm directory (DMS users and roles) from Keycloak in a single
     * snapshot.
     *
     * @throws IllegalStateException if the admin client secret is unconfigured or
     *     Keycloak returns an invalid response
     */
    public DirectorySnapshot loadDirectory() {
        String accessToken = requestAccessToken();
        List<RoleSummary> roles = loadRoles(accessToken);
        List<UserSummary> users = loadUsers(accessToken);
        return new DirectorySnapshot(users, roles);
    }

    private String requestAccessToken() {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Keycloak admin client secret is not configured");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        String json =
                restClient
                        .post()
                        .uri(baseUrl + "/realms/{realm}/protocol/openid-connect/token", realm)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);

        JsonNode response = parseJson(json);
        String token = response.path("access_token").asText();
        if (token.isBlank()) {
            throw new IllegalStateException("Keycloak token response did not contain an access token");
        }
        return token;
    }

    private List<RoleSummary> loadRoles(String accessToken) {
        JsonNode response =
                getJson(
                        baseUrl + "/admin/realms/{realm}/roles",
                        accessToken,
                        realm);
        List<RoleSummary> roles = new ArrayList<>();
        response.forEach(
                role -> {
                    String name = role.path("name").asText();
                    if (name.startsWith("dms_")) {
                        roles.add(
                                new RoleSummary(
                                        name,
                                        nullIfBlank(role.path("description").asText())));
                    }
                });
        roles.sort(Comparator.comparing(RoleSummary::name));
        return roles;
    }

    private List<UserSummary> loadUsers(String accessToken) {
        JsonNode response =
                getJson(
                        baseUrl + "/admin/realms/{realm}/users?max=500&briefRepresentation=false",
                        accessToken,
                        realm);
        List<UserSummary> users = new ArrayList<>();
        response.forEach(
                user -> {
                    if (!user.path("serviceAccountClientId").asText().isBlank()) {
                        return;
                    }
                    String id = user.path("id").asText();
                    users.add(
                            new UserSummary(
                                    id,
                                    user.path("username").asText(),
                                    displayName(user),
                                    nullIfBlank(user.path("email").asText()),
                                    firstAttribute(user.path("attributes"), "department"),
                                    loadRealmRoles(accessToken, id),
                                    user.path("enabled").asBoolean(false)));
                });
        users.sort(Comparator.comparing(UserSummary::username, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    private List<String> loadRealmRoles(String accessToken, String userId) {
        JsonNode response =
                getJson(
                        baseUrl
                                + "/admin/realms/{realm}/users/{userId}/role-mappings/realm/composite",
                        accessToken,
                        realm,
                        userId);
        List<String> roles = new ArrayList<>();
        response.forEach(
                role -> {
                    String name = role.path("name").asText();
                    if (name.startsWith("dms_")) {
                        roles.add(name);
                    }
                });
        roles.sort(String.CASE_INSENSITIVE_ORDER);
        return roles;
    }

    private JsonNode getJson(String uri, String accessToken, Object... uriVariables) {
        String json =
                restClient
                        .get()
                        .uri(uri, uriVariables)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .retrieve()
                        .body(String.class);
        return parseJson(json);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Keycloak returned an invalid JSON response", exception);
        }
    }

    private static String displayName(JsonNode user) {
        String fullName =
                (user.path("firstName").asText() + " " + user.path("lastName").asText()).trim();
        return fullName.isBlank() ? user.path("username").asText() : fullName;
    }

    private static String firstAttribute(JsonNode attributes, String name) {
        JsonNode values = attributes.path(name);
        return values.isArray() && !values.isEmpty()
                ? nullIfBlank(values.get(0).asText())
                : null;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    /** Immutable pairing of the realm's DMS users and roles returned by a directory load. */
    public record DirectorySnapshot(List<UserSummary> users, List<RoleSummary> roles) {}
}
