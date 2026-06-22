package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.repository.FolderRepository;
import de.hof.dms.service.PermissionService.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionServiceTest {

    private FolderRepository folderRepository;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        service = new PermissionService(folderRepository);
    }

    // ---- helpers ----------------------------------------------------------

    private CurrentUser user(String username, String department, String... roles) {
        return new CurrentUser(username + "-id", username, username + "@dms.local", department, List.of(roles));
    }

    private FolderAccess access(boolean r, boolean c, boolean u, boolean d, boolean m) {
        FolderAccess a = new FolderAccess();
        a.setRead(r);
        a.setCreate(c);
        a.setUpdate(u);
        a.setDelete(d);
        a.setManagePermissions(m);
        return a;
    }

    private FolderAcl acl(
            String owner,
            String ownerDept,
            List<String> roles,
            List<String> depts,
            FolderAccess access,
            boolean inherit) {
        FolderAcl acl = new FolderAcl();
        acl.setOwner(owner);
        acl.setOwnerDepartment(ownerDept);
        acl.setAllowedUserIds(List.of());
        acl.setAllowedRoles(roles);
        acl.setAllowedDepartments(depts);
        acl.setAccess(access);
        acl.setInheritFromParent(inherit);
        return acl;
    }

    private DocumentRecord doc(FolderAcl acl, String department, String folderId) {
        DocumentRecord d = new DocumentRecord();
        d.setId("doc-1");
        d.setOrganizationalUnit(department);
        d.setFolderId(folderId);
        d.setAcl(acl);
        return d;
    }

    // ---- tests ------------------------------------------------------------

    @Test
    void adminCanDoEverything() {
        CurrentUser admin = user("admin", null, "dms_admin");
        DocumentRecord d = doc(acl("someone", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), false), "ITDLZ", null);
        for (Action action : Action.values()) {
            assertThat(service.canDocument(admin, d, action)).as(action.name()).isTrue();
        }
    }

    @Test
    void ownerCanDoEverything() {
        CurrentUser owner = user("alice", "ITDLZ", "dms_contributor");
        DocumentRecord d = doc(acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), false), "ITDLZ", null);
        for (Action action : Action.values()) {
            assertThat(service.canDocument(owner, d, action)).as(action.name()).isTrue();
        }
    }

    @Test
    void viewerWithReadAclCanReadButNotWrite() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");
        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of("dms_viewer"), List.of(), access(true, false, false, false, false), false),
                "ITDLZ",
                null);
        assertThat(service.canDocument(viewer, d, Action.READ)).isTrue();
        assertThat(service.canDocument(viewer, d, Action.CREATE)).isFalse();
        assertThat(service.canDocument(viewer, d, Action.UPDATE)).isFalse();
        assertThat(service.canDocument(viewer, d, Action.DELETE)).isFalse();
        assertThat(service.canDocument(viewer, d, Action.MANAGE_PERMISSIONS)).isFalse();
    }

    @Test
    void viewerWithoutAnyAclIsDenied() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");
        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of("dms_contributor"), List.of(), access(true, true, true, true, true), false),
                "ITDLZ",
                null);
        assertThat(service.canDocument(viewer, d, Action.READ)).isFalse();
    }

    @Test
    void contributorCanCreateInReadableFolderButNotDeleteOthersDocuments() {
        CurrentUser contributor = user("bob", "ITDLZ", "dms_contributor");
        Folder folder = new Folder();
        folder.setId("f1");
        folder.setAcl(acl("alice", "ITDLZ", List.of("dms_contributor"), List.of(), access(true, false, false, false, false), false));

        assertThat(service.canFolder(contributor, folder, Action.READ)).isTrue();
        assertThat(service.canFolder(contributor, folder, Action.CREATE)).isTrue();

        DocumentRecord othersDoc = doc(
                acl("alice", "ITDLZ", List.of("dms_contributor"), List.of(), access(true, false, false, false, false), false),
                "ITDLZ",
                null);
        assertThat(service.canDocument(contributor, othersDoc, Action.DELETE)).isFalse();
    }

    @Test
    void contributorCanDeleteOwnDocument() {
        CurrentUser contributor = user("bob", "ITDLZ", "dms_contributor");
        DocumentRecord ownDoc = doc(
                acl("bob", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), false),
                "ITDLZ",
                null);
        assertThat(service.canDocument(contributor, ownDoc, Action.DELETE)).isTrue();
    }

    @Test
    void managerCanManageWithinOwnDepartmentOnly() {
        CurrentUser manager = user("mgr", "ITDLZ", "dms_department_manager");
        DocumentRecord sameDept = doc(
                acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), false),
                "ITDLZ",
                null);
        DocumentRecord otherDept = doc(
                acl("alice", "FINANCE", List.of(), List.of(), access(false, false, false, false, false), false),
                "FINANCE",
                null);

        assertThat(service.canDocument(manager, sameDept, Action.UPDATE)).isTrue();
        assertThat(service.canDocument(manager, sameDept, Action.DELETE)).isTrue();
        assertThat(service.canDocument(manager, sameDept, Action.MANAGE_PERMISSIONS)).isTrue();
        assertThat(service.canDocument(manager, otherDept, Action.READ)).isFalse();
    }

    @Test
    void documentInheritsParentFolderAclWhenInheritTrue() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");

        Folder parent = new Folder();
        parent.setId("parent-1");
        parent.setAcl(acl("alice", "ITDLZ", List.of("dms_viewer"), List.of(), access(true, false, false, false, false), false));
        when(folderRepository.findById("parent-1")).thenReturn(Optional.of(parent));

        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), true),
                "ITDLZ",
                "parent-1");

        assertThat(service.canDocument(viewer, d, Action.READ)).isTrue();
    }

    @Test
    void inheritFalseUsesOnlyDirectAcl() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");

        Folder parent = new Folder();
        parent.setId("parent-1");
        parent.setAcl(acl("alice", "ITDLZ", List.of("dms_viewer"), List.of(), access(true, false, false, false, false), false));

        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), false),
                "ITDLZ",
                "parent-1");

        assertThat(service.canDocument(viewer, d, Action.READ)).isFalse();
    }

    @Test
    void inheritanceWalksUpMultipleLevels() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");

        Folder grandparent = new Folder();
        grandparent.setId("gp");
        grandparent.setAcl(acl("alice", "ITDLZ", List.of("dms_viewer"), List.of(), access(true, false, false, false, false), false));

        Folder parent = new Folder();
        parent.setId("parent-1");
        parent.setParentId("gp");
        parent.setAcl(acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), true));

        when(folderRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(folderRepository.findById("gp")).thenReturn(Optional.of(grandparent));

        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of(), List.of(), access(false, false, false, false, false), true),
                "ITDLZ",
                "parent-1");

        assertThat(service.canDocument(viewer, d, Action.READ)).isTrue();
    }

    @Test
    void departmentMembershipGrantsAccessViaAcl() {
        CurrentUser viewer = user("viewer", "ITDLZ", "dms_viewer");
        DocumentRecord d = doc(
                acl("alice", "ITDLZ", List.of(), List.of("ITDLZ"), access(true, false, false, false, false), false),
                "ITDLZ",
                null);
        assertThat(service.canDocument(viewer, d, Action.READ)).isTrue();
    }
}
