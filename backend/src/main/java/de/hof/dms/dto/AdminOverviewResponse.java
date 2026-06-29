package de.hof.dms.dto;

import java.util.List;

public record AdminOverviewResponse(
        List<UserSummary> users,
        List<RoleSummary> roles,
        List<PermissionScopeSummary> permissionScopes,
        List<ComponentHealth> health,
        SystemMetrics metrics) {

    public record UserSummary(
            String id,
            String username,
            String displayName,
            String email,
            String department,
            List<String> roles,
            boolean enabled) {}

    public record RoleSummary(String name, String description) {}

    public record PermissionScopeSummary(
            String name, String resource, String description, List<String> grantedBy) {}

    public record ComponentHealth(String component, String status, String detail) {}

    public record SystemMetrics(long users, long documents, long folders) {}
}
