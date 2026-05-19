package de.hof.dms.domain;

public class FolderAccess {

    private boolean read;
    private boolean create;
    private boolean update;
    private boolean delete;
    private boolean managePermissions;

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isManagePermissions() {
        return managePermissions;
    }

    public void setManagePermissions(boolean managePermissions) {
        this.managePermissions = managePermissions;
    }
}
