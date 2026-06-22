package de.hof.dms.dto;

import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAcl;

import java.time.Instant;

/** Full view of a folder document, returned when a folder is created. */
public record FolderResponse(
        String id,
        String name,
        String path,
        String parentId,
        Instant createdAt,
        String owner,
        String ownerDepartment,
        boolean inheritFromParent) {

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
