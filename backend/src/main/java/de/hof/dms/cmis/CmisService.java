package de.hof.dms.cmis;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.dto.CreateFolderRequest;
import de.hof.dms.dto.DocumentUploadResponse;
import de.hof.dms.dto.FolderResponse;
import de.hof.dms.dto.SearchResultEntry;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.DocumentService;
import de.hof.dms.service.FolderService;
import de.hof.dms.service.PermissionService;
import de.hof.dms.service.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CMIS 1.1 operations (Browser/JSON binding) over the DMS data model. Every
 * operation enforces the same RBAC as the REST API by reusing
 * {@link PermissionService} and the existing document/folder services, so a
 * caller can never see or mutate anything through CMIS that the REST API would
 * deny. Object-type mapping: {@code cmis:folder} ↔ {@code folders},
 * {@code cmis:document} ↔ {@code documents}.
 */
@Service
@Profile("!no-mongo")
public class CmisService {

    private static final String ROOT_PATH = "/";
    private static final int DEFAULT_QUERY_LIMIT = 20;

    private static final Pattern CONTAINS =
            Pattern.compile("CONTAINS\\(\\s*'([^']*)'\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_EQUALS =
            Pattern.compile("cmis:name\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionService permissionService;
    private final FolderService folderService;
    private final DocumentService documentService;
    private final SearchService searchService;

    private final String repositoryId;
    private final String repositoryName;

    /** Wires the reused repositories/services and the configured repository id and name. */
    public CmisService(
            FolderRepository folderRepository,
            DocumentRepository documentRepository,
            PermissionService permissionService,
            FolderService folderService,
            DocumentService documentService,
            SearchService searchService,
            @Value("${dms.cmis.repository-id:dms}") String repositoryId,
            @Value("${dms.cmis.repository-name:DMS Repository}") String repositoryName) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.permissionService = permissionService;
        this.folderService = folderService;
        this.documentService = documentService;
        this.searchService = searchService;
        this.repositoryId = repositoryId;
        this.repositoryName = repositoryName;
    }

    // ---- repository info --------------------------------------------------

    /** getRepositories: a map keyed by repository id, as the Browser binding expects. */
    public Map<String, Object> getRepositoryInfos() {
        Map<String, Object> repositories = new LinkedHashMap<>();
        repositories.put(repositoryId, getRepositoryInfo(repositoryId));
        return repositories;
    }

    /**
     * getRepositoryInfo: repository metadata and capabilities for the given id.
     *
     * @throws CmisException if the requested repository id is not this repository
     */
    public Map<String, Object> getRepositoryInfo(String requestedRepositoryId) {
        requireRepository(requestedRepositoryId);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("repositoryId", repositoryId);
        info.put("repositoryName", repositoryName);
        info.put("repositoryDescription", "Hof DMS minimal CMIS interface");
        info.put("cmisVersionSupported", "1.1");
        info.put("productName", "dms-backend");
        info.put("rootFolderId", folderRepository.findByPath(ROOT_PATH).map(Folder::getId).orElse(null));
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("capabilityQuery", "metadataonly");
        capabilities.put("capabilityContentStreamUpdatability", "anytime");
        info.put("capabilities", capabilities);
        return info;
    }

    // ---- reads ------------------------------------------------------------

    /**
     * getObject: resolves a folder or active document by id into its CMIS representation.
     *
     * @throws CmisException if the object is missing or the user lacks read permission
     */
    public Map<String, Object> getObject(String requestedRepositoryId, String objectId, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        return resolveObject(objectId, user);
    }

    /**
     * getChildren: the readable folders and active documents under a folder, paged
     * by {@code skipCount}/{@code maxItems}. Only entries the user may read are included.
     *
     * @throws CmisException if the folder is missing or unreadable
     */
    public Map<String, Object> getChildren(
            String requestedRepositoryId,
            String folderId,
            int maxItems,
            int skipCount,
            CurrentUser user) {
        requireRepository(requestedRepositoryId);
        Folder folder = resolveFolder(folderId);
        if (!permissionService.canFolder(user, folder, PermissionService.Action.READ)) {
            throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot read folder " + folder.getId());
        }

        List<Map<String, Object>> children = new ArrayList<>();
        folderRepository.findByParentIdOrderByNameAsc(folder.getId()).stream()
                .filter(f -> permissionService.canFolder(user, f, PermissionService.Action.READ))
                .forEach(f -> children.add(CmisObjects.entry(CmisObjects.folder(f))));
        documentRepository
                .findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
                        folder.getId(), DocumentService.STATUS_ACTIVE)
                .stream()
                .filter(d -> permissionService.canDocument(user, d, PermissionService.Action.READ))
                .forEach(d -> children.add(CmisObjects.entry(CmisObjects.document(d))));

        int total = children.size();
        int from = Math.min(Math.max(skipCount, 0), total);
        int to = maxItems > 0 ? Math.min(from + maxItems, total) : total;
        List<Map<String, Object>> page = new ArrayList<>(children.subList(from, to));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objects", page);
        result.put("hasMoreItems", to < total);
        result.put("numItems", total);
        return result;
    }

    /**
     * getObjectParents: the single parent folder of a folder or document, or an
     * empty list for the root.
     *
     * @throws CmisException if the object is missing or unreadable
     */
    public List<Map<String, Object>> getParents(
            String requestedRepositoryId, String objectId, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        String parentId;
        Optional<Folder> folder = folderRepository.findById(notBlank(objectId));
        if (folder.isPresent()) {
            if (!permissionService.canFolder(user, folder.get(), PermissionService.Action.READ)) {
                throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot read object " + objectId);
            }
            parentId = folder.get().getParentId();
        } else {
            DocumentRecord document = activeDocument(objectId);
            if (!permissionService.canDocument(user, document, PermissionService.Action.READ)) {
                throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot read object " + objectId);
            }
            parentId = document.getFolderId();
        }

        if (parentId == null || parentId.isBlank()) {
            return List.of();
        }
        Folder parent =
                folderRepository
                        .findById(parentId)
                        .orElseThrow(
                                () -> new CmisException(CmisFault.OBJECT_NOT_FOUND, "Parent not found"));
        return List.of(CmisObjects.entry(CmisObjects.folder(parent)));
    }

    /** getContentStream: returns the binary payload via the existing document service. */
    public DocumentService.DownloadPayload getContentStream(
            String requestedRepositoryId, String objectId, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        try {
            return documentService.download(notBlank(objectId), user);
        } catch (ApiException ex) {
            throw CmisException.fromApi(ex);
        }
    }

    // ---- query ------------------------------------------------------------

    /**
     * query: runs a restricted CMIS-SQL statement over the RBAC-enforced search
     * index. Only {@code CONTAINS('...')} full-text and {@code cmis:name = '...'}
     * predicates are supported.
     *
     * @throws CmisException if the statement is blank or uses an unsupported predicate
     */
    public Map<String, Object> query(
            String requestedRepositoryId,
            String statement,
            int maxItems,
            int skipCount,
            CurrentUser user) {
        requireRepository(requestedRepositoryId);
        if (statement == null || statement.isBlank()) {
            throw new CmisException(CmisFault.INVALID_ARGUMENT, "query statement is required");
        }

        int limit = maxItems > 0 ? maxItems : DEFAULT_QUERY_LIMIT;
        int page = skipCount > 0 ? skipCount / limit : 0;

        List<SearchResultEntry> hits;
        Matcher contains = CONTAINS.matcher(statement);
        Matcher nameEquals = NAME_EQUALS.matcher(statement);
        if (contains.find()) {
            // Same RBAC-enforced full-text path as REST search.
            hits = searchService.search(contains.group(1), user, page, limit);
        } else if (nameEquals.find()) {
            hits = searchService.searchByName(nameEquals.group(1), user, page, limit);
        } else {
            throw new CmisException(
                    CmisFault.INVALID_ARGUMENT,
                    "Only CONTAINS('...') and cmis:name = '...' predicates are supported");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        hits.forEach(hit -> results.add(CmisObjects.documentFromSearch(hit)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", results);
        result.put("hasMoreItems", false);
        result.put("numItems", results.size());
        return result;
    }

    // ---- writes -----------------------------------------------------------

    /**
     * createFolder: creates a folder under {@code parentId} from CMIS properties
     * (requires {@code cmis:name}), delegating RBAC and validation to the folder service.
     *
     * @throws CmisException translated from any {@link ApiException} the folder service raises
     */
    public Map<String, Object> createFolder(
            String requestedRepositoryId, String parentId, Map<String, String> properties, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        String name = required(properties, "cmis:name");
        Boolean inherit =
                properties.containsKey("dms:inheritFromParent")
                        ? Boolean.valueOf(properties.get("dms:inheritFromParent"))
                        : null;
        try {
            FolderResponse created =
                    folderService.createFolder(
                            new CreateFolderRequest(name, blankToNull(parentId), inherit), user);
            Folder folder =
                    folderRepository
                            .findById(created.id())
                            .orElseThrow(
                                    () -> new CmisException(
                                            CmisFault.RUNTIME, "Created folder vanished"));
            return CmisObjects.folder(folder);
        } catch (ApiException ex) {
            throw CmisException.fromApi(ex);
        }
    }

    /**
     * createDocument: uploads a new document into {@code parentId} (or the root when
     * absent) from the content stream and CMIS properties (requires {@code cmis:name}).
     *
     * @throws CmisException translated from any {@link ApiException} the document service raises
     */
    public Map<String, Object> createDocument(
            String requestedRepositoryId,
            String parentId,
            MultipartFile content,
            Map<String, String> properties,
            CurrentUser user) {
        requireRepository(requestedRepositoryId);
        String name = required(properties, "cmis:name");
        String documentType = properties.getOrDefault("dms:documentType", "document");
        String description = properties.get("dms:description");
        String folderId = blankToNull(parentId) != null ? parentId : rootId();
        try {
            DocumentUploadResponse uploaded =
                    documentService.upload(
                            content, name, documentType, folderId, description, null, true, user);
            DocumentRecord record =
                    documentRepository
                            .findById(uploaded.id())
                            .orElseThrow(
                                    () -> new CmisException(
                                            CmisFault.RUNTIME, "Created document vanished"));
            return CmisObjects.document(record);
        } catch (ApiException ex) {
            throw CmisException.fromApi(ex);
        }
    }

    /**
     * setContentStream: replaces a document's content and returns its refreshed representation.
     *
     * @throws CmisException translated from any {@link ApiException} the document service raises
     */
    public Map<String, Object> setContentStream(
            String requestedRepositoryId, String objectId, MultipartFile content, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        try {
            documentService.setContent(notBlank(objectId), content, user);
            return resolveObject(objectId, user);
        } catch (ApiException ex) {
            throw CmisException.fromApi(ex);
        }
    }

    /**
     * delete: soft-deletes a document or removes an empty non-root folder, subject to
     * DELETE permission. Deleting the root or a non-empty folder is a constraint violation.
     *
     * @throws CmisException on missing object, denied permission, or a constraint violation
     */
    public void deleteObject(String requestedRepositoryId, String objectId, CurrentUser user) {
        requireRepository(requestedRepositoryId);
        String id = notBlank(objectId);

        Optional<DocumentRecord> document = documentRepository.findById(id);
        if (document.isPresent()
                && !DocumentService.STATUS_DELETED.equals(document.get().getDocumentStatus())) {
            try {
                documentService.delete(id, user); // soft-delete + DELETE permission check
            } catch (ApiException ex) {
                throw CmisException.fromApi(ex);
            }
            return;
        }

        Folder folder =
                folderRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new CmisException(CmisFault.OBJECT_NOT_FOUND, "Object not found"));
        if (ROOT_PATH.equals(folder.getPath())) {
            throw new CmisException(CmisFault.CONSTRAINT, "The root folder cannot be deleted");
        }
        if (!permissionService.canFolder(user, folder, PermissionService.Action.DELETE)) {
            throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot delete folder " + id);
        }
        if (folderHasChildren(folder.getId())) {
            // deleteTree is out of scope for the MVP; a non-empty folder is a constraint violation.
            throw new CmisException(CmisFault.CONSTRAINT, "Folder is not empty");
        }
        folderRepository.delete(folder);
    }

    // ---- internals --------------------------------------------------------

    private Map<String, Object> resolveObject(String objectId, CurrentUser user) {
        Optional<Folder> folder = folderRepository.findById(folderOrRootId(objectId));
        if (folder.isPresent()) {
            if (!permissionService.canFolder(user, folder.get(), PermissionService.Action.READ)) {
                throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot read object " + objectId);
            }
            return CmisObjects.folder(folder.get());
        }
        DocumentRecord document = activeDocument(objectId);
        if (!permissionService.canDocument(user, document, PermissionService.Action.READ)) {
            throw new CmisException(CmisFault.PERMISSION_DENIED, "Cannot read object " + objectId);
        }
        return CmisObjects.document(document);
    }

    private boolean folderHasChildren(String folderId) {
        if (!folderRepository.findByParentIdOrderByNameAsc(folderId).isEmpty()) {
            return true;
        }
        return !documentRepository
                .findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
                        folderId, DocumentService.STATUS_ACTIVE)
                .isEmpty();
    }

    private Folder resolveFolder(String folderId) {
        if (folderId == null || folderId.isBlank()) {
            return root();
        }
        return folderRepository
                .findById(folderId)
                .orElseThrow(() -> new CmisException(CmisFault.OBJECT_NOT_FOUND, "Folder not found"));
    }

    private DocumentRecord activeDocument(String objectId) {
        DocumentRecord record =
                documentRepository
                        .findById(notBlank(objectId))
                        .orElseThrow(
                                () -> new CmisException(CmisFault.OBJECT_NOT_FOUND, "Object not found"));
        if (DocumentService.STATUS_DELETED.equals(record.getDocumentStatus())) {
            throw new CmisException(CmisFault.OBJECT_NOT_FOUND, "Object not found");
        }
        return record;
    }

    private Folder root() {
        return folderRepository
                .findByPath(ROOT_PATH)
                .orElseThrow(
                        () -> new CmisException(CmisFault.OBJECT_NOT_FOUND, "Root folder not found"));
    }

    private String rootId() {
        return root().getId();
    }

    private String folderOrRootId(String objectId) {
        return (objectId == null || objectId.isBlank()) ? rootId() : objectId;
    }

    private void requireRepository(String requestedRepositoryId) {
        if (requestedRepositoryId != null && !repositoryId.equals(requestedRepositoryId)) {
            throw new CmisException(
                    CmisFault.OBJECT_NOT_FOUND, "Unknown repository " + requestedRepositoryId);
        }
    }

    private static String required(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new CmisException(CmisFault.INVALID_ARGUMENT, key + " is required");
        }
        return value.trim();
    }

    private static String notBlank(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw new CmisException(CmisFault.INVALID_ARGUMENT, "objectId is required");
        }
        return objectId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
