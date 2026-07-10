package de.hof.dms.dto;

/**
 * Returned by GET /api/documents/{id}/permissions.
 * `effective` is always populated for the requesting user.
 * `acl` is only populated for admins or users with managePermissions; otherwise null.
 */
public record PermissionsResponse(EffectivePermissions effective, AclDto acl) {

    /** The resolved permission flags the requesting user actually has on the document. */
    public record EffectivePermissions(
            boolean read,
            boolean create,
            boolean update,
            boolean delete,
            boolean managePermissions) {}
}
