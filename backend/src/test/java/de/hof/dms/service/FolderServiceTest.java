package de.hof.dms.service;

import de.hof.dms.domain.Folder;
import de.hof.dms.dto.CreateFolderRequest;
import de.hof.dms.dto.FolderResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderServiceTest {

    private FolderRepository folderRepository;
    private DocumentRepository documentRepository;
    private PermissionService permissionService;
    private FolderService service;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        documentRepository = mock(DocumentRepository.class);
        permissionService = mock(PermissionService.class);
        service = new FolderService(folderRepository, documentRepository, permissionService);
        // save() echoes the persisted entity back, like Spring Data does.
        when(folderRepository.save(any(Folder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CurrentUser contributor() {
        return new CurrentUser(
                "contributor-id",
                "contributor",
                "contributor@dms.local",
                "ITDLZ",
                List.of("dms_contributor"));
    }

    private Folder root() {
        Folder root = new Folder();
        root.setId("root-id");
        root.setName("/");
        root.setPath("/");
        return root;
    }

    @Test
    void rootChildPathIsComputed() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));
        when(folderRepository.findByPath("/2026/")).thenReturn(Optional.empty());

        FolderResponse response =
                service.createFolder(new CreateFolderRequest("2026", null, null), contributor());

        assertThat(response.path()).isEqualTo("/2026/");

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertThat(captor.getValue().getPath()).isEqualTo("/2026/");
        assertThat(captor.getValue().getParentId()).isEqualTo("root-id");
    }

    @Test
    void nestedChildPathIsComputedFromParentPath() {
        Folder finance = new Folder();
        finance.setId("finance-id");
        finance.setName("Finance");
        finance.setPath("/Finance/");
        when(folderRepository.findById("finance-id")).thenReturn(Optional.of(finance));
        when(folderRepository.findByPath("/Finance/2026/")).thenReturn(Optional.empty());

        FolderResponse response =
                service.createFolder(
                        new CreateFolderRequest("2026", "finance-id", null), contributor());

        assertThat(response.path()).isEqualTo("/Finance/2026/");
    }

    @Test
    void creatingFolderSetsAclFromCurrentUserAndInheritsByDefault() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));
        when(folderRepository.findByPath("/Reports/")).thenReturn(Optional.empty());

        service.createFolder(new CreateFolderRequest("Reports", null, null), contributor());

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        Folder saved = captor.getValue();
        assertThat(saved.getAcl().getOwner()).isEqualTo("contributor");
        assertThat(saved.getAcl().getOwnerDepartment()).isEqualTo("ITDLZ");
        assertThat(saved.getAcl().isInheritFromParent()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void inheritFromParentCanBeDisabled() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));
        when(folderRepository.findByPath("/Standalone/")).thenReturn(Optional.empty());

        service.createFolder(
                new CreateFolderRequest("Standalone", null, false), contributor());

        ArgumentCaptor<Folder> captor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(captor.capture());
        assertThat(captor.getValue().getAcl().isInheritFromParent()).isFalse();
    }

    @Test
    void creatingUnderMissingParentReturns404() {
        when(folderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.createFolder(
                                        new CreateFolderRequest("x", "missing", null),
                                        contributor()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateFolderNameReturns409() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));
        when(folderRepository.findByPath("/Reports/")).thenReturn(Optional.of(new Folder()));

        assertThatThrownBy(
                        () ->
                                service.createFolder(
                                        new CreateFolderRequest("Reports", null, null),
                                        contributor()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void blankNameReturns400() {
        assertThatThrownBy(
                        () ->
                                service.createFolder(
                                        new CreateFolderRequest("  ", null, null), contributor()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listFoldersWithoutParentReturnsChildrenOfRoot() {
        Folder root = root();
        Folder child = new Folder();
        child.setId("child-id");
        child.setName("Finance");
        child.setPath("/Finance/");

        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root));
        when(folderRepository.findByParentIdOrderByNameAsc("root-id"))
                .thenReturn(List.of(child));
        when(permissionService.canFolder(any(), any(), any())).thenReturn(true);

        var page = service.listFolders(null, 0, 20, contributor());

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).id()).isEqualTo("child-id");
    }

    @Test
    void listFoldersPaginatesFilteredResults() {
        Folder root = root();
        Folder a = childFolder("a-id", "A");
        Folder b = childFolder("b-id", "B");
        Folder c = childFolder("c-id", "C");

        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root));
        when(folderRepository.findByParentIdOrderByNameAsc("root-id"))
                .thenReturn(List.of(a, b, c));
        when(permissionService.canFolder(any(), any(), any())).thenReturn(true);

        var firstPage = service.listFolders(null, 0, 2, contributor());
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        var secondPage = service.listFolders(null, 1, 2, contributor());
        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.content().get(0).id()).isEqualTo("c-id");
    }

    private Folder childFolder(String id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name + "/");
        return folder;
    }
}
