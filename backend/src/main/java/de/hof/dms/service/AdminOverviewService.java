package de.hof.dms.service;

import de.hof.dms.dto.AdminOverviewResponse;
import de.hof.dms.dto.AdminOverviewResponse.ComponentHealth;
import de.hof.dms.dto.AdminOverviewResponse.PermissionScopeSummary;
import de.hof.dms.dto.AdminOverviewResponse.SystemMetrics;
import de.hof.dms.dto.AdminOverviewResponse.UserSummary;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the administrator dashboard overview: the Keycloak user/role
 * directory, aggregate document and folder counts, a summary of the permission
 * scopes, and live health of the backing components (Keycloak, MongoDB). Each
 * component is probed defensively so a single outage degrades gracefully rather
 * than failing the whole overview.
 */
@Service
@Profile("!no-mongo")
public class AdminOverviewService {

    private final KeycloakAdminService keycloakAdminService;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Creates the overview service with the Keycloak directory gateway,
     * document and folder repositories, and the Mongo template used for health probes.
     */
    public AdminOverviewService(
            KeycloakAdminService keycloakAdminService,
            DocumentRepository documentRepository,
            FolderRepository folderRepository,
            MongoTemplate mongoTemplate) {
        this.keycloakAdminService = keycloakAdminService;
        this.documentRepository = documentRepository;
        this.folderRepository = folderRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Builds the full admin overview, collecting the user directory, document and
     * folder counts, permission scopes, and per-component health status.
     */
    public AdminOverviewResponse getOverview() {
        List<ComponentHealth> health = new ArrayList<>();
        health.add(new ComponentHealth("Backend API", "UP", "Administrative API is responding"));

        KeycloakAdminService.DirectorySnapshot directory = loadDirectory(health);
        long documents = countDocuments(health);
        long folders = countFolders();

        return new AdminOverviewResponse(
                directory.users(),
                directory.roles(),
                permissionScopes(),
                health,
                new SystemMetrics(directory.users().size(), documents, folders));
    }

    /**
     * Loads the Keycloak user/role directory, appending an UP or DOWN health entry;
     * returns an empty snapshot if Keycloak is unavailable.
     *
     * @param health the running list of component health entries to append to
     * @return the directory snapshot, or an empty one on failure
     */
    private KeycloakAdminService.DirectorySnapshot loadDirectory(List<ComponentHealth> health) {
        try {
            KeycloakAdminService.DirectorySnapshot directory = keycloakAdminService.loadDirectory();
            health.add(
                    new ComponentHealth(
                            "Keycloak",
                            "UP",
                            directory.users().size() + " users available"));
            return directory;
        } catch (Exception exception) {
            health.add(
                    new ComponentHealth(
                            "Keycloak",
                            "DOWN",
                            safeMessage(exception, "User directory is unavailable")));
            return new KeycloakAdminService.DirectorySnapshot(List.of(), List.of());
        }
    }

    /**
     * Pings MongoDB and counts stored documents, appending an UP or DOWN health entry;
     * returns zero if the database is unavailable.
     *
     * @param health the running list of component health entries to append to
     * @return the total document count, or zero on failure
     */
    private long countDocuments(List<ComponentHealth> health) {
        try {
            Document result = mongoTemplate.executeCommand("{ ping: 1 }");
            boolean available = result.getDouble("ok") == 1.0;
            health.add(
                    new ComponentHealth(
                            "MongoDB",
                            available ? "UP" : "DOWN",
                            available ? "Database ping succeeded" : "Database ping failed"));
            return documentRepository.count();
        } catch (Exception exception) {
            health.add(
                    new ComponentHealth(
                            "MongoDB",
                            "DOWN",
                            safeMessage(exception, "Database is unavailable")));
            return 0;
        }
    }

    /**
     * Counts stored folders, returning zero if the repository cannot be reached.
     *
     * @return the total folder count, or zero on failure
     */
    private long countFolders() {
        try {
            return folderRepository.count();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * Returns the static catalogue of permission scopes (read, create, update,
     * delete, manage_permissions) and the roles that grant each, shown on the dashboard.
     */
    private List<PermissionScopeSummary> permissionScopes() {
        return List.of(
                new PermissionScopeSummary(
                        "read",
                        "documents and folders",
                        "View metadata, folder contents, and download documents",
                        List.of("admin", "owner", "department manager", "matching ACL")),
                new PermissionScopeSummary(
                        "create",
                        "documents and folders",
                        "Upload documents or create content in an allowed folder",
                        List.of("admin", "owner", "department manager", "contributor with read access", "matching ACL")),
                new PermissionScopeSummary(
                        "update",
                        "documents and folders",
                        "Change document metadata, content, or folder properties",
                        List.of("admin", "owner", "department manager", "matching ACL")),
                new PermissionScopeSummary(
                        "delete",
                        "documents and folders",
                        "Delete an allowed document or folder",
                        List.of("admin", "owner", "department manager", "matching ACL")),
                new PermissionScopeSummary(
                        "manage_permissions",
                        "documents and folders",
                        "Change ACL membership, inheritance, and access flags",
                        List.of("admin", "owner", "department manager", "matching ACL")));
    }

    /**
     * Returns the exception's message, or the fallback when it is null or blank.
     *
     * @param exception the exception whose message is preferred
     * @param fallback the text to use when no usable message is present
     * @return a non-blank health detail message
     */
    private static String safeMessage(Exception exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
