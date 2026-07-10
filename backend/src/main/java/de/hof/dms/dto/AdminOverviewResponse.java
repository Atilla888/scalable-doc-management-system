package de.hof.dms.dto;

import java.util.List;

/**
 * Aggregated payload for the admin dashboard endpoint: the user and role
 * catalogue, the configured permission scopes, per-component health status and
 * high-level system counts. The nested records model each section of that
 * overview.
 *
 * @param users the user account catalogue
 * @param roles the role definitions
 * @param permissionScopes the configured permission scopes
 * @param health the per-component health statuses
 * @param metrics the high-level system counts
 */
public record AdminOverviewResponse(
        List<UserSummary> users,
        List<RoleSummary> roles,
        List<PermissionScopeSummary> permissionScopes,
        List<ComponentHealth> health,
        SystemMetrics metrics) {

    /**
     * A single user account with its profile fields, assigned roles and enabled state.
     *
     * @param id the user's unique identifier
     * @param username the login name
     * @param displayName the human-readable display name
     * @param email the user's email address
     * @param department the user's department
     * @param roles the roles assigned to the user
     * @param enabled whether the account is enabled
     */
    public record UserSummary(
            String id,
            String username,
            String displayName,
            String email,
            String department,
            List<String> roles,
            boolean enabled) {}

    /**
     * A role definition (name plus human-readable description).
     *
     * @param name the role name
     * @param description the human-readable description
     */
    public record RoleSummary(String name, String description) {}

    /**
     * A permission scope: the guarded resource and which roles grant it.
     *
     * @param name the scope name
     * @param resource the guarded resource
     * @param description the human-readable description
     * @param grantedBy the roles that grant this scope
     */
    public record PermissionScopeSummary(
            String name, String resource, String description, List<String> grantedBy) {}

    /**
     * Health status ({@code status}/{@code detail}) of one system component.
     *
     * @param component the component name
     * @param status the component's health status
     * @param detail additional status detail
     */
    public record ComponentHealth(String component, String status, String detail) {}

    /**
     * Aggregate counts of users, documents and folders in the system.
     *
     * @param users the total number of users
     * @param documents the total number of documents
     * @param folders the total number of folders
     */
    public record SystemMetrics(long users, long documents, long folders) {}
}
