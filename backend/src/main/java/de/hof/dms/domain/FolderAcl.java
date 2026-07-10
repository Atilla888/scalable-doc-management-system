package de.hof.dms.domain;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Access-control list embedded in a {@link Folder} (and in
 * {@link de.hof.dms.domain.DocumentRecord}). It names the {@code owner} and
 * owning department, and lists the users, roles and departments allowed access
 * together with the concrete permission flags in {@link FolderAccess}. When
 * {@code inheritFromParent} is {@code true} the effective permissions are
 * derived from the parent folder rather than from this list.
 */
public class FolderAcl {

    private String owner;

    @Field("owner_department")
    private String ownerDepartment;

    @Field("allowed_user_ids")
    private List<String> allowedUserIds;

    @Field("allowed_roles")
    private List<String> allowedRoles;

    @Field("allowed_departments")
    private List<String> allowedDepartments;

    private FolderAccess access;

    private boolean inheritFromParent;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerDepartment() {
        return ownerDepartment;
    }

    public void setOwnerDepartment(String ownerDepartment) {
        this.ownerDepartment = ownerDepartment;
    }

    public List<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(List<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public List<String> getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(List<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public List<String> getAllowedDepartments() {
        return allowedDepartments;
    }

    public void setAllowedDepartments(List<String> allowedDepartments) {
        this.allowedDepartments = allowedDepartments;
    }

    public FolderAccess getAccess() {
        return access;
    }

    public void setAccess(FolderAccess access) {
        this.access = access;
    }

    public boolean isInheritFromParent() {
        return inheritFromParent;
    }

    public void setInheritFromParent(boolean inheritFromParent) {
        this.inheritFromParent = inheritFromParent;
    }
}
