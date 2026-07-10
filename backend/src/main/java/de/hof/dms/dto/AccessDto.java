package de.hof.dms.dto;

import de.hof.dms.domain.FolderAccess;

/**
 * API-layer view of the permission flags in {@link FolderAccess} (read, create,
 * update, delete, managePermissions). Carried inside {@link AclDto} and mapped
 * to/from the domain object via {@link #from(FolderAccess)} and
 * {@link #toAccess()}.
 *
 * @param read whether reading is permitted
 * @param create whether creating child items is permitted
 * @param update whether updating is permitted
 * @param delete whether deleting is permitted
 * @param managePermissions whether managing permissions is permitted
 */
public record AccessDto(
        boolean read,
        boolean create,
        boolean update,
        boolean delete,
        boolean managePermissions) {

    /**
     * Maps a domain {@link FolderAccess} to a DTO, defaulting all flags to
     * {@code false} when {@code access} is {@code null}.
     *
     * @param access the domain access flags, may be {@code null}
     * @return the corresponding DTO
     */
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

    /**
     * Converts this DTO back into a domain {@link FolderAccess}.
     *
     * @return the domain access flags carrying this DTO's values
     */
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
