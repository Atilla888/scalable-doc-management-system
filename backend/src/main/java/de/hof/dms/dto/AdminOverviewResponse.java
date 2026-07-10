package de.hof.dms.dto;

import java.util.List;

/**
 * Aggregated payload for the admin dashboard endpoint: the user and role
 * catalogue, the configured permission scopes, per-component health status and
 * high-level system counts. The nested records model each section of that
 * overview.
 */
public record AdminOverviewResponse(
        List<UserSummary> users,
        List<RoleSummary> roles,
        List<PermissionScopeSummary> permissionScopes,
        List<ComponentHealth> health,
        SystemMetrics metrics) {

    /** A single user account with its profile fields, assigned roles and enabled state. */
    public record UserSummary(
            String id,
            String username,
            String displayName,
            String email,
            String department,
            List<String> roles,
            boolean enabled) {}

    /** A role definition (name plus human-readable description). */
    public record RoleSummary(String name, String description) {}

    /** A permission scope: the guarded resource and which roles grant it. */
    public record PermissionScopeSummary(
            String name, String resource, String description, List<String> grantedBy) {}

    /** Health status ({@code status}/{@code detail}) of one system component. */
    public record ComponentHealth(String component, String status, String detail) {}

    /** Aggregate counts of users, documents and folders in the system. */
    public record SystemMetrics(long users, long documents, long folders) {}
}
