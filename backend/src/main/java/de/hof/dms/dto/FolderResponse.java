package de.hof.dms.dto;

import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAcl;

import java.time.Instant;

/**
 * Full view of a folder document, returned when a folder is created.
 *
 * @param id the folder's unique identifier
 * @param name the folder's display name
 * @param path the folder's full path within the tree
 * @param parentId the id of the parent folder, or {@code null} for a root folder
 * @param createdAt the instant the folder was created
 * @param owner the id of the folder's owner, or {@code null} when no ACL is set
 * @param ownerDepartment the owning department, or {@code null} when no ACL is set
 * @param inheritFromParent whether the folder inherits its parent's ACL
 */
public record FolderResponse(
        String id,
        String name,
        String path,
        String parentId,
        Instant createdAt,
        String owner,
        String ownerDepartment,
        boolean inheritFromParent) {

    /**
     * Builds a response from a {@link Folder}, flattening its ACL owner fields.
     *
     * @param folder the folder to expose
     * @return the corresponding response view
     */
    public static FolderResponse from(Folder folder) {
        FolderAcl acl = folder.getAcl();
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getPath(),
                folder.getParentId(),
                folder.getCreatedAt(),
                acl != null ? acl.getOwner() : null,
                acl != null ? acl.getOwnerDepartment() : null,
                acl != null && acl.isInheritFromParent());
    }
}
