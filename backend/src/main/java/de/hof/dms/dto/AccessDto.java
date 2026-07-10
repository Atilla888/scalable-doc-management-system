package de.hof.dms.dto;

import de.hof.dms.domain.FolderAccess;

/**
 * API-layer view of the permission flags in {@link FolderAccess} (read, create,
 * update, delete, managePermissions). Carried inside {@link AclDto} and mapped
 * to/from the domain object via {@link #from(FolderAccess)} and
 * {@link #toAccess()}.
 */
public record AccessDto(
        boolean read,
        boolean create,
        boolean update,
        boolean delete,
        boolean managePermissions) {

    public static AccessDto from(FolderAccess access) {
        if (access == null) {
            return new AccessDto(false, false, false, false, false);
        }
        return new AccessDto(
                access.isRead(),
                access.isCreate(),
                access.isUpdate(),
                access.isDelete(),
                access.isManagePermissions());
    }

    public FolderAccess toAccess() {
        FolderAccess access = new FolderAccess();
        access.setRead(read);
        access.setCreate(create);
        access.setUpdate(update);
        access.setDelete(delete);
        access.setManagePermissions(managePermissions);
        return access;
    }
}
