package de.hof.dms.cmis;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.dto.SearchResultEntry;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.DocumentService;
import de.hof.dms.service.FolderService;
import de.hof.dms.service.PermissionService;
import de.hof.dms.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CmisServiceTest {

    private FolderRepository folderRepository;
    private DocumentRepository documentRepository;
    private PermissionService permissionService;
    private FolderService folderService;
    private DocumentService documentService;
    private SearchService searchService;
    private CmisService service;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        documentRepository = mock(DocumentRepository.class);
        permissionService = mock(PermissionService.class);
        folderService = mock(FolderService.class);
        documentService = mock(DocumentService.class);
        searchService = mock(SearchService.class);
        service =
                new CmisService(
                        folderRepository,
                        documentRepository,
                        permissionService,
                        folderService,
                        documentService,
                        searchService,
                        "dms",
                        "DMS Repository");
    }

    private CurrentUser viewer() {
        return new CurrentUser("viewer-id", "viewer", "v@dms.local", null, List.of("dms_viewer"));
    }

    private Folder root() {
        Folder root = new Folder();
        root.setId("root-id");
        root.setName("/");
        root.setPath("/");
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object> object) {
        return (Map<String, Object>) object.get("succinctProperties");
    }

    @Test
    void repositoryInfoReturnsIdAndName() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));

        Map<String, Object> info = service.getRepositoryInfo("dms");

        assertThat(info.get("repositoryId")).isEqualTo("dms");
        assertThat(info.get("repositoryName")).isEqualTo("DMS Repository");
        assertThat(info.get("rootFolderId")).isEqualTo("root-id");
        assertThat(info.get("cmisVersionSupported")).isEqualTo("1.1");
    }

    @Test
    void repositoryInfosAreKeyedByRepositoryId() {
        when(folderRepository.findByPath("/")).thenReturn(Optional.of(root()));

        Map<String, Object> repositories = service.getRepositoryInfos();

        assertThat(repositories).containsKey("dms");
    }

    @Test
    void unknownRepositoryIsObjectNotFound() {
        assertThatThrownBy(() -> service.getRepositoryInfo("other"))
                .isInstanceOf(CmisException.class)
                .extracting(ex -> ((CmisException) ex).getFault())
                .isEqualTo(CmisFault.OBJECT_NOT_FOUND);
    }

    @Test
    void getObjectMapsFolderToCmisFolderType() {
        Folder folder = new Folder();
        folder.setId("f1");
        folder.setName("Finance");
        folder.setPath("/Finance/");
        when(folderRepository.findById("f1")).thenReturn(Optional.of(folder));
        when(permissionService.canFolder(any(), eq(folder), eq(PermissionService.Action.READ)))
                .thenReturn(true);

        Map<String, Object> object = service.getObject("dms", "f1", viewer());

        assertThat(props(object).get("cmis:objectId")).isEqualTo("f1");
        assertThat(props(object).get("cmis:baseTypeId")).isEqualTo("cmis:folder");
        assertThat(props(object).get("cmis:path")).isEqualTo("/Finance/");
    }

    @Test
    void getObjectOnUnauthorizedDocumentRaisesPermissionDenied() {
        when(folderRepository.findById("d1")).thenReturn(Optional.empty());
        DocumentRecord doc = new DocumentRecord();
        doc.setId("d1");
        doc.setDocumentStatus("active");
        when(documentRepository.findById("d1")).thenReturn(Optional.of(doc));
        when(permissionService.canDocument(any(), eq(doc), eq(PermissionService.Action.READ)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getObject("dms", "d1", viewer()))
                .isInstanceOf(CmisException.class)
                .extracting(ex -> ((CmisException) ex).getFault())
                .isEqualTo(CmisFault.PERMISSION_DENIED);
    }

    @Test
    void queryWithContainsDelegatesToRbacSearch() {
        SearchResultEntry hit =
                new SearchResultEntry(
                        "doc-9", "Quarterly", "EAP-1", "report", "snippet", "root-id",
                        Instant.now(), "not_required");
        when(searchService.search(eq("budget"), any(), eq(0), eq(20))).thenReturn(List.of(hit));

        Map<String, Object> result =
                service.query("dms", "SELECT * FROM cmis:document WHERE CONTAINS('budget')", 20, 0, viewer());

        assertThat(result.get("numItems")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
        assertThat(props(results.get(0)).get("cmis:objectId")).isEqualTo("doc-9");
    }

    @Test
    void unsupportedQueryPredicateIsInvalidArgument() {
        assertThatThrownBy(
                        () -> service.query("dms", "SELECT * FROM cmis:document", 20, 0, viewer()))
                .isInstanceOf(CmisException.class)
                .extracting(ex -> ((CmisException) ex).getFault())
                .isEqualTo(CmisFault.INVALID_ARGUMENT);
    }
}
