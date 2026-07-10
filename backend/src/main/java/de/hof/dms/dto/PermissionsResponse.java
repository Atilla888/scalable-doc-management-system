package de.hof.dms.dto;

/**
 * Returned by GET /api/documents/{id}/permissions.
 * `effective` is always populated for the requesting user.
 * `acl` is only populated for admins or users with managePermissions; otherwise null.
 *
 * @param effective the permission flags the requesting user has on the document
 * @param acl       the full access-control list, or {@code null} when the caller
 *                  may not view it
 */
public record PermissionsResponse(EffectivePermissions effective, AclDto acl) {

    /**
     * The resolved permission flags the requesting user actually has on the document.
     *
     * @param read              whether the user may read the document
     * @param create            whether the user may create content under the document
     * @param update            whether the user may update the document
     * @param delete            whether the user may delete the document
     * @param managePermissions whether the user may manage the document's permissions
     */
    public record EffectivePermissions(
            boolean read,
            boolean create,
            boolean update,
            boolean delete,
            boolean managePermissions) {}
}
