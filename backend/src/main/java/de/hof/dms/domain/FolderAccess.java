package de.hof.dms.domain;

/**
 * The set of permission flags granted by a {@link FolderAcl}: read, create,
 * update, delete and managePermissions. Embedded inside {@code FolderAcl} and
 * mirrored to the API layer by {@link de.hof.dms.dto.AccessDto}.
 */
public class FolderAccess {

    private boolean read;
    private boolean create;
    private boolean update;
    private boolean delete;
    private boolean managePermissions;

    /** Returns whether read access is granted. */
    public boolean isRead() {
        return read;
    }

    /** Sets whether read access is granted. */
    public void setRead(boolean read) {
        this.read = read;
    }

    /** Returns whether create access is granted. */
    public boolean isCreate() {
        return create;
    }

    /** Sets whether create access is granted. */
    public void setCreate(boolean create) {
        this.create = create;
    }

    /** Returns whether update access is granted. */
    public boolean isUpdate() {
        return update;
    }

    /** Sets whether update access is granted. */
    public void setUpdate(boolean update) {
        this.update = update;
    }

    /** Returns whether delete access is granted. */
    public boolean isDelete() {
        return delete;
    }

    /** Sets whether delete access is granted. */
    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    /** Returns whether permission-management access is granted. */
    public boolean isManagePermissions() {
        return managePermissions;
    }

    /** Sets whether permission-management access is granted. */
    public void setManagePermissions(boolean managePermissions) {
        this.managePermissions = managePermissions;
    }
}
