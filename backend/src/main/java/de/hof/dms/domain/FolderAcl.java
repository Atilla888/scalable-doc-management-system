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

    /** Returns the identifier of the owning user. */
    public String getOwner() {
        return owner;
    }

    /** Sets the identifier of the owning user. */
    public void setOwner(String owner) {
        this.owner = owner;
    }

    /** Returns the owning department. */
    public String getOwnerDepartment() {
        return ownerDepartment;
    }

    /** Sets the owning department. */
    public void setOwnerDepartment(String ownerDepartment) {
        this.ownerDepartment = ownerDepartment;
    }

    /** Returns the list of user identifiers granted access. */
    public List<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    /** Sets the list of user identifiers granted access. */
    public void setAllowedUserIds(List<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    /** Returns the list of roles granted access. */
    public List<String> getAllowedRoles() {
        return allowedRoles;
    }

    /** Sets the list of roles granted access. */
    public void setAllowedRoles(List<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    /** Returns the list of departments granted access. */
    public List<String> getAllowedDepartments() {
        return allowedDepartments;
    }

    /** Sets the list of departments granted access. */
    public void setAllowedDepartments(List<String> allowedDepartments) {
        this.allowedDepartments = allowedDepartments;
    }

    /** Returns the concrete permission flags granted by this ACL. */
    public FolderAccess getAccess() {
        return access;
    }

    /** Sets the concrete permission flags granted by this ACL. */
    public void setAccess(FolderAccess access) {
        this.access = access;
    }

    /** Returns whether effective permissions are inherited from the parent folder. */
    public boolean isInheritFromParent() {
        return inheritFromParent;
    }

    /** Sets whether effective permissions are inherited from the parent folder. */
    public void setInheritFromParent(boolean inheritFromParent) {
        this.inheritFromParent = inheritFromParent;
    }
}
