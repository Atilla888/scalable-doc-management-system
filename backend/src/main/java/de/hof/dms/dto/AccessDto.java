package de.hof.dms.dto;

import de.hof.dms.domain.FolderAccess;

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
