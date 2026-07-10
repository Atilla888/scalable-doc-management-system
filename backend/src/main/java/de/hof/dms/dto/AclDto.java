package de.hof.dms.dto;

import de.hof.dms.domain.FolderAcl;

import java.util.ArrayList;
import java.util.List;

/**
 * API-layer view of a {@link FolderAcl}: owner, owning department, the allowed
 * user/role/department lists, the effective {@link AccessDto} flags and the
 * {@code inheritFromParent} switch. Mapped to/from the domain object via
 * {@link #from(FolderAcl)} and {@link #toAcl()}; used when reading or updating
 * folder/document permissions.
 */
public record AclDto(
        String owner,
        String ownerDepartment,
        List<String> allowedUserIds,
        List<String> allowedRoles,
        List<String> allowedDepartments,
        AccessDto access,
        boolean inheritFromParent) {

    public static AclDto from(FolderAcl acl) {
        if (acl == null) {
            return null;
        }
        return new AclDto(
                acl.getOwner(),
                acl.getOwnerDepartment(),
                acl.getAllowedUserIds(),
                acl.getAllowedRoles(),
                acl.getAllowedDepartments(),
                AccessDto.from(acl.getAccess()),
                acl.isInheritFromParent());
    }

    public FolderAcl toAcl() {
        FolderAcl acl = new FolderAcl();
        acl.setOwner(owner);
        acl.setOwnerDepartment(ownerDepartment);
        acl.setAllowedUserIds(allowedUserIds != null ? new ArrayList<>(allowedUserIds) : new ArrayList<>());
        acl.setAllowedRoles(allowedRoles != null ? new ArrayList<>(allowedRoles) : new ArrayList<>());
        acl.setAllowedDepartments(
                allowedDepartments != null ? new ArrayList<>(allowedDepartments) : new ArrayList<>());
        acl.setAccess(access != null ? access.toAccess() : new AccessDto(false, false, false, false, false).toAccess());
        acl.setInheritFromParent(inheritFromParent);
        return acl;
    }
}
