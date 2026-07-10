package de.hof.dms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.hof.dms.dto.AdminOverviewResponse.RoleSummary;
import de.hof.dms.dto.AdminOverviewResponse.UserSummary;
import de.hof.dms.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gateway to the Keycloak Admin REST API. Authenticates with the
 * client-credentials grant of the {@code dms-admin-api} service account, loads
 * the realm's DMS users and {@code dms_} roles for the administrator overview,
 * and maintains the {@code department} attribute of individual users for
 * department assignment. Only DMS-scoped roles and non-service accounts are
 * surfaced. The service account holds only the {@code realm-management} roles
 * it needs (query/view users, view realm, manage users) — not realm-admin.
 */
@Service
public class KeycloakAdminService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    /**
     * Creates the gateway with a REST client, JSON mapper, and the Keycloak admin
     * connection settings (base URL, realm, and client credentials) from configuration.
     */
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

    /**
     * Sets or removes the {@code department} attribute of one Keycloak user,
     * preserving all other attributes and fields. Requires the
     * {@code manage-users} realm-management role on the service account.
     *
     * <p>The update echoes the user's <em>full</em> fetched representation with
     * only the department attribute modified. Keycloak validates the whole user
     * profile against the submitted representation, so a partial body that omits
     * built-in fields (email, first/last name) is rejected with
     * {@code error-user-attribute-required}. Echoing the fetched values keeps
     * username, email, names, roles, credentials, and enabled state untouched.
     *
     * @param userId the Keycloak user id
     * @param departmentCode the normalized department code to set, or null to remove
     * @return true when the user was updated, false when no such user exists
     * @throws ApiException 502 with a sanitized reason when Keycloak rejects the update
     * @throws IllegalStateException if the admin client secret is unconfigured
     */
    public boolean updateUserDepartment(String userId, String departmentCode) {
        String accessToken = requestAccessToken();
        JsonNode user;
        try {
            user =
                    getJson(
                            baseUrl + "/admin/realms/{realm}/users/{userId}",
                            accessToken,
                            realm,
                            userId);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw toApiException("load the user from Keycloak", exception);
        }

        if (!(user instanceof ObjectNode userNode)) {
            throw new IllegalStateException("Keycloak returned an unexpected user representation");
        }
        ObjectNode attributes =
                userNode.hasNonNull("attributes") && userNode.get("attributes").isObject()
                        ? (ObjectNode) userNode.get("attributes")
                        : userNode.putObject("attributes");
        if (departmentCode == null) {
            attributes.remove("department");
        } else {
            attributes.putArray("department").add(departmentCode);
        }

        try {
            restClient
                    .put()
                    .uri(baseUrl + "/admin/realms/{realm}/users/{userId}", realm, userId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userNode.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toApiException("update the user in Keycloak", exception);
        }
        return true;
    }

    /**
     * Translates a Keycloak Admin API error response into a 502 {@link ApiException}
     * whose detail names the failed operation and Keycloak's error message — but
     * never tokens, secrets, or the raw response.
     *
     * @param operation short description of what was being attempted
     * @param exception the failed Keycloak call
     * @return the exception to throw
     */
    private static ApiException toApiException(
            String operation, RestClientResponseException exception) {
        String reason = "HTTP " + exception.getStatusCode().value();
        try {
            JsonNode body = new ObjectMapper().readTree(exception.getResponseBodyAsString());
            String message =
                    body.hasNonNull("errorMessage")
                            ? body.get("errorMessage").asText()
                            : body.path("error").asText();
            if (message != null && !message.isBlank()) {
                reason += ", " + message;
                if (body.hasNonNull("field")) {
                    reason += " (field: " + body.get("field").asText() + ")";
                }
            }
        } catch (Exception ignored) {
            // Non-JSON error body — the HTTP status alone is still useful.
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Keycloak refused to " + operation + " [" + reason + "]");
    }

    /**
     * Checks whether any realm user still carries the given department attribute,
     * which blocks deleting that department.
     *
     * @param departmentCode the normalized department code
     * @return true when at least one user has the department assigned
     */
    public boolean hasUsersWithDepartment(String departmentCode) {
        String accessToken = requestAccessToken();
        JsonNode response =
                getJson(
                        baseUrl
                                + "/admin/realms/{realm}/users?q={query}&max=1&briefRepresentation=true",
                        accessToken,
                        realm,
                        "department:" + departmentCode);
        return response.isArray() && !response.isEmpty();
    }

    /**
     * Obtains an admin access token via the client-credentials grant.
     *
     * @return the bearer access token
     * @throws IllegalStateException if the client secret is unconfigured or no token is returned
     */
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

    /**
     * Loads the realm roles, keeping only {@code dms_}-prefixed roles sorted by name.
     *
     * @param accessToken the admin bearer token
     * @return the DMS role summaries
     */
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

    /**
     * Loads realm users (excluding service accounts) with their DMS realm roles,
     * sorted case-insensitively by username.
     *
     * @param accessToken the admin bearer token
     * @return the user summaries
     */
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

    /**
     * Loads a user's effective (composite) realm roles, keeping only {@code dms_}
     * roles sorted case-insensitively.
     *
     * @param accessToken the admin bearer token
     * @param userId the Keycloak user id
     * @return the user's DMS realm role names
     */
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

    /**
     * Performs a bearer-authenticated GET and parses the JSON response body.
     *
     * @param uri the request URI template
     * @param accessToken the admin bearer token
     * @param uriVariables the values expanding the URI template
     * @return the parsed response body
     */
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

    /**
     * Parses a JSON string into a tree.
     *
     * @param json the raw JSON text
     * @return the parsed JSON tree
     * @throws IllegalStateException if the response is not valid JSON
     */
    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Keycloak returned an invalid JSON response", exception);
        }
    }

    /**
     * Derives a display name from a user's first and last name, falling back to the
     * username when both are blank.
     *
     * @param user the user JSON node
     * @return the display name
     */
    private static String displayName(JsonNode user) {
        String fullName =
                (user.path("firstName").asText() + " " + user.path("lastName").asText()).trim();
        return fullName.isBlank() ? user.path("username").asText() : fullName;
    }

    /**
     * Returns the first value of a named multi-valued user attribute, or null when
     * absent or blank.
     *
     * @param attributes the user's attributes node
     * @param name the attribute name
     * @return the first attribute value, or null
     */
    private static String firstAttribute(JsonNode attributes, String name) {
        JsonNode values = attributes.path(name);
        return values.isArray() && !values.isEmpty()
                ? nullIfBlank(values.get(0).asText())
                : null;
    }

    /**
     * Returns the value unchanged, or null when it is null or blank.
     *
     * @param value the value to check
     * @return the value, or null when blank
     */
    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Removes a single trailing slash from the value if present.
     *
     * @param value the value to normalize
     * @return the value without a trailing slash
     */
    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    /**
     * Immutable pairing of the realm's DMS users and roles returned by a directory load.
     *
     * @param users the DMS user summaries
     * @param roles the DMS role summaries
     */
    public record DirectorySnapshot(List<UserSummary> users, List<RoleSummary> roles) {}
}
