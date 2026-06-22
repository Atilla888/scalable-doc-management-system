package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.FolderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Central RBAC resolver. Decides whether a user may perform an action on a
 * document or folder following the architecture permission model:
 *
 * <ol>
 *   <li>{@code dms_admin} → always allow</li>
 *   <li>resource owner → allow all actions</li>
 *   <li>{@code dms_department_manager} whose department matches the resource → allow all actions</li>
 *   <li>direct ACL membership (user id / role / department) gated by the ACL's access flags</li>
 *   <li>inherited ACL from the parent folder chain when {@code inheritFromParent = true}</li>
 *   <li>{@code dms_contributor} may CREATE inside any folder it can READ</li>
 *   <li>otherwise → deny</li>
 * </ol>
 */
@Service
@Profile("!no-mongo")
public class PermissionService {

    public enum Action {
        READ,
        CREATE,
        UPDATE,
        DELETE,
        MANAGE_PERMISSIONS
    }

    public static final String ROLE_ADMIN = "dms_admin";
    public static final String ROLE_MANAGER = "dms_department_manager";
    public static final String ROLE_CONTRIBUTOR = "dms_contributor";

    private static final int MAX_DEPTH = 64;

    private final FolderRepository folderRepository;

    public PermissionService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    // ---- public API -------------------------------------------------------

    public boolean canDocument(CurrentUser user, DocumentRecord doc, Action action) {
        if (isAdmin(user)) {
            return true;
        }
        String department =
                doc.getOrganizationalUnit() != null
                        ? doc.getOrganizationalUnit()
                        : departmentOf(doc.getAcl());
        if (grantsAtLevel(user, doc.getAcl(), department, action)) {
            return true;
        }
        FolderAcl acl = doc.getAcl();
        if (acl != null && acl.isInheritFromParent() && doc.getFolderId() != null) {
            Folder parent = folderRepository.findById(doc.getFolderId()).orElse(null);
            if (parent != null) {
                return folderChainGrants(user, parent, action);
            }
        }
        return false;
    }

    public boolean canFolder(CurrentUser user, Folder folder, Action action) {
        if (isAdmin(user)) {
            return true;
        }
        if (action == Action.CREATE
                && hasRole(user, ROLE_CONTRIBUTOR)
                && canFolder(user, folder, Action.READ)) {
            return true;
        }
        return folderChainGrants(user, folder, action);
    }

    public void requireDocument(CurrentUser user, DocumentRecord doc, Action action) {
        if (!canDocument(user, doc, action)) {
            throw forbidden(action);
        }
    }

    public void requireFolder(CurrentUser user, Folder folder, Action action) {
        if (!canFolder(user, folder, action)) {
            throw forbidden(action);
        }
    }

    public boolean canManageDocument(CurrentUser user, DocumentRecord doc) {
        return isAdmin(user) || canDocument(user, doc, Action.MANAGE_PERMISSIONS);
    }

    // ---- internals --------------------------------------------------------

    private boolean folderChainGrants(CurrentUser user, Folder start, Action action) {
        Folder current = start;
        int depth = 0;
        while (current != null && depth < MAX_DEPTH) {
            FolderAcl acl = current.getAcl();
            if (grantsAtLevel(user, acl, departmentOf(acl), action)) {
                return true;
            }
            if (acl != null && acl.isInheritFromParent() && current.getParentId() != null) {
                current = folderRepository.findById(current.getParentId()).orElse(null);
                depth++;
            } else {
                break;
            }
        }
        return false;
    }

    /** Owner, matching department manager, or ACL membership + access flag. */
    private boolean grantsAtLevel(CurrentUser user, FolderAcl acl, String department, Action action) {
        if (acl == null) {
            return false;
        }
        if (user.username() != null && user.username().equals(acl.getOwner())) {
            return true;
        }
        if (hasRole(user, ROLE_MANAGER)
                && department != null
                && !department.isBlank()
                && department.equalsIgnoreCase(user.department())) {
            return true;
        }
        return membershipMatches(user, acl) && accessAllows(acl.getAccess(), action);
    }

    private boolean membershipMatches(CurrentUser user, FolderAcl acl) {
        if (acl.getAllowedUserIds() != null
                && user.username() != null
                && acl.getAllowedUserIds().contains(user.username())) {
            return true;
        }
        if (acl.getAllowedRoles() != null
                && user.roles().stream().anyMatch(acl.getAllowedRoles()::contains)) {
            return true;
        }
        if (acl.getAllowedDepartments() != null && user.department() != null) {
            return acl.getAllowedDepartments().stream()
                    .anyMatch(d -> d != null && d.equalsIgnoreCase(user.department()));
        }
        return false;
    }

    private boolean accessAllows(FolderAccess access, Action action) {
        if (access == null) {
            return false;
        }
        return switch (action) {
            case READ -> access.isRead();
            case CREATE -> access.isCreate();
            case UPDATE -> access.isUpdate();
            case DELETE -> access.isDelete();
            case MANAGE_PERMISSIONS -> access.isManagePermissions();
        };
    }

    private String departmentOf(FolderAcl acl) {
        return acl != null ? acl.getOwnerDepartment() : null;
    }

    private boolean isAdmin(CurrentUser user) {
        return hasRole(user, ROLE_ADMIN);
    }

    private boolean hasRole(CurrentUser user, String role) {
        return user.roles() != null && user.roles().contains(role);
    }

    private ApiException forbidden(Action action) {
        return new ApiException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to " + action.name().toLowerCase() + " this resource");
    }
}
