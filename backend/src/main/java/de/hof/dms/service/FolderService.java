package de.hof.dms.service;

import de.hof.dms.domain.DocumentRecord;
import de.hof.dms.domain.Folder;
import de.hof.dms.domain.FolderAccess;
import de.hof.dms.domain.FolderAcl;
import de.hof.dms.dto.BreadcrumbEntry;
import de.hof.dms.dto.CreateFolderRequest;
import de.hof.dms.dto.DocumentSummary;
import de.hof.dms.dto.FolderPage;
import de.hof.dms.dto.FolderResponse;
import de.hof.dms.dto.FolderSummary;
import de.hof.dms.dto.FolderViewResponse;
import de.hof.dms.dto.UpdateFolderRequest;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Profile("!no-mongo")
public class FolderService {

    private static final String ROOT_PATH = "/";
    private static final int MAX_BREADCRUMB_DEPTH = 64;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final PermissionService permissionService;

    public FolderService(
            FolderRepository folderRepository,
            DocumentRepository documentRepository,
            PermissionService permissionService) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.permissionService = permissionService;
    }

    public FolderViewResponse getRootView(CurrentUser user) {
        Folder root =
                folderRepository
                        .findByPath(ROOT_PATH)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Root folder not found"));
        return buildView(root, user);
    }

    public FolderViewResponse getView(String folderId, CurrentUser user) {
        Folder folder =
                folderRepository
                        .findById(folderId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Folder not found"));
        return buildView(folder, user);
    }

    /**
     * Lists the child folders of {@code parentId} (or of the root folder when
     * {@code parentId} is blank), restricted to folders the user may READ, with
     * pagination applied after the permission filter so the totals reflect what
     * the caller can actually see.
     */
    /**
     * Flat list of every folder the caller may <b>read</b>, ordered by path.
     * Used by the UI as the destination picker when moving a folder.
     */
    public List<FolderSummary> listAllFolders(CurrentUser user) {
        return folderRepository.findAll().stream()
                .filter(f -> permissionService.canFolder(user, f, PermissionService.Action.READ))
                .sorted(
                        java.util.Comparator.comparing(
                                Folder::getPath,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(FolderSummary::from)
                .toList();
    }

    public FolderPage listFolders(String parentId, int page, int size, CurrentUser user) {
        Folder parent = resolveParent(parentId);
        permissionService.requireFolder(user, parent, PermissionService.Action.READ);

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<FolderSummary> readable =
                folderRepository.findByParentIdOrderByNameAsc(parent.getId()).stream()
                        .filter(f -> permissionService.canFolder(user, f, PermissionService.Action.READ))
                        .map(FolderSummary::from)
                        .toList();

        int total = readable.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<FolderSummary> content = new ArrayList<>(readable.subList(from, to));
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new FolderPage(content, safePage, safeSize, total, totalPages);
    }

    /**
     * Creates a folder under the given parent (or under the root folder when no
     * parent is supplied). The materialized {@code path} is derived from the
     * parent path, the ACL is seeded from the creating user's identity, and a
     * second root folder can never be created because a non-empty name always
     * produces a path other than {@code "/"}.
     */
    public FolderResponse createFolder(CreateFolderRequest request, CurrentUser user) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "name is required");
        }
        String name = request.name().trim();
        if (name.contains("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "name must not contain '/'");
        }

        Folder parent = resolveParent(request.parentId());
        permissionService.requireFolder(user, parent, PermissionService.Action.CREATE);

        // Parent paths always end with '/', so this yields e.g. /Finance/ + 2026 -> /Finance/2026/.
        String path = parent.getPath() + name + "/";
        if (folderRepository.findByPath(path).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A folder with this name already exists here");
        }

        boolean inheritFromParent =
                request.inheritFromParent() == null || request.inheritFromParent();

        Folder folder = new Folder();
        folder.setName(name);
        folder.setPath(path);
        folder.setParentId(parent.getId());
        folder.setCreatedAt(Instant.now());
        folder.setAcl(buildFolderAcl(user, inheritFromParent));

        return FolderResponse.from(folderRepository.save(folder));
    }

    /**
     * Renames and/or moves a folder. The materialized {@code path} of the folder
     * and of its entire subtree is rewritten so paths stay consistent. Requires
     * <b>update</b> on the folder, plus <b>create</b> on the destination parent
     * when moving. The root folder cannot be modified, a folder cannot be moved
     * into itself or a descendant, and the destination must not already contain a
     * sibling with the same name.
     */
    public FolderResponse updateFolder(String id, UpdateFolderRequest request, CurrentUser user) {
        Folder folder =
                folderRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (ROOT_PATH.equals(folder.getPath())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The root folder cannot be modified");
        }
        permissionService.requireFolder(user, folder, PermissionService.Action.UPDATE);

        String newName =
                request != null && request.name() != null && !request.name().isBlank()
                        ? request.name().trim()
                        : folder.getName();
        if (newName.contains("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "name must not contain '/'");
        }

        boolean moving =
                request != null
                        && request.parentId() != null
                        && !request.parentId().isBlank()
                        && !request.parentId().equals(folder.getParentId());

        Folder newParent;
        if (moving) {
            newParent =
                    folderRepository
                            .findById(request.parentId())
                            .orElseThrow(
                                    () -> new ApiException(
                                            HttpStatus.NOT_FOUND, "Parent folder not found"));
            if (newParent.getId().equals(folder.getId())
                    || newParent.getPath().startsWith(folder.getPath())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "A folder cannot be moved into itself or one of its descendants");
            }
            permissionService.requireFolder(user, newParent, PermissionService.Action.CREATE);
        } else {
            newParent = parentOf(folder);
        }

        String parentPath = newParent != null ? newParent.getPath() : ROOT_PATH;
        String newPath = parentPath + newName + "/";

        if (!newPath.equals(folder.getPath()) && folderRepository.findByPath(newPath).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A folder with this name already exists here");
        }

        // Rewrite the path prefix for the folder and every descendant.
        String oldPath = folder.getPath();
        List<Folder> subtree = folderRepository.findByPathStartingWith(oldPath);
        Folder target = folder;
        for (Folder node : subtree) {
            node.setPath(newPath + node.getPath().substring(oldPath.length()));
            if (node.getId().equals(folder.getId())) {
                target = node;
            }
        }
        target.setName(newName);
        if (moving) {
            target.setParentId(newParent.getId());
        }
        folderRepository.saveAll(subtree);
        return FolderResponse.from(target);
    }

    /**
     * Deletes a folder. Requires <b>delete</b> on the folder; the root folder can
     * never be deleted. A non-empty folder is rejected unless {@code recursive}
     * is set, in which case the whole subtree is removed and the documents it
     * contains are soft-deleted.
     */
    public void deleteFolder(String id, boolean recursive, CurrentUser user) {
        Folder folder =
                folderRepository
                        .findById(id)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (ROOT_PATH.equals(folder.getPath())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The root folder cannot be deleted");
        }
        permissionService.requireFolder(user, folder, PermissionService.Action.DELETE);

        if (!recursive) {
            if (folderHasChildren(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "Folder is not empty");
            }
            folderRepository.delete(folder);
            return;
        }

        List<Folder> subtree = folderRepository.findByPathStartingWith(folder.getPath());
        for (Folder node : subtree) {
            List<DocumentRecord> documents =
                    documentRepository.findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
                            node.getId(), DocumentService.STATUS_ACTIVE);
            documents.forEach(doc -> doc.setDocumentStatus(DocumentService.STATUS_DELETED));
            if (!documents.isEmpty()) {
                documentRepository.saveAll(documents);
            }
        }
        folderRepository.deleteAll(subtree);
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

    private Folder parentOf(Folder folder) {
        if (folder.getParentId() == null || folder.getParentId().isBlank()) {
            return null;
        }
        return folderRepository.findById(folder.getParentId()).orElse(null);
    }

    private Folder resolveParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return folderRepository
                    .findByPath(ROOT_PATH)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, "Root folder not found"));
        }
        return folderRepository
                .findById(parentId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Parent folder not found"));
    }

    /**
     * Default ACL for a freshly created folder: owned by the creator, who keeps
     * full access via the RBAC owner rule. {@code inheritFromParent} (true by
     * default) lets the resolver walk up to the parent chain, so colleagues who
     * can read the parent area can also read this folder. Broader sharing is
     * granted explicitly later (Issue 15).
     */
    private FolderAcl buildFolderAcl(CurrentUser user, boolean inheritFromParent) {
        FolderAcl acl = new FolderAcl();
        acl.setOwner(user.username());
        acl.setOwnerDepartment(user.department());
        acl.setAllowedUserIds(new ArrayList<>(List.of(user.username())));
        acl.setAllowedRoles(new ArrayList<>());
        acl.setAllowedDepartments(new ArrayList<>());

        FolderAccess access = new FolderAccess();
        access.setRead(true);
        access.setCreate(true);
        access.setUpdate(true);
        access.setDelete(true);
        access.setManagePermissions(true);
        acl.setAccess(access);

        acl.setInheritFromParent(inheritFromParent);
        return acl;
    }

    private FolderViewResponse buildView(Folder folder, CurrentUser user) {
        permissionService.requireFolder(user, folder, PermissionService.Action.READ);

        // Only list children the user is allowed to read; nothing is hidden by the UI.
        List<FolderSummary> subfolders =
                folderRepository.findByParentIdOrderByNameAsc(folder.getId()).stream()
                        .filter(f -> permissionService.canFolder(user, f, PermissionService.Action.READ))
                        .map(FolderSummary::from)
                        .toList();

        List<DocumentSummary> documents =
                documentRepository
                        .findByFolderIdAndDocumentStatusOrderByUploadDateDesc(
                                folder.getId(), DocumentService.STATUS_ACTIVE)
                        .stream()
                        .filter(d -> permissionService.canDocument(user, d, PermissionService.Action.READ))
                        .map(DocumentSummary::from)
                        .toList();

        return new FolderViewResponse(
                FolderSummary.from(folder), buildBreadcrumb(folder), subfolders, documents);
    }

    private List<BreadcrumbEntry> buildBreadcrumb(Folder folder) {
        List<BreadcrumbEntry> trail = new ArrayList<>();
        Folder current = folder;
        int depth = 0;
        while (current != null && depth < MAX_BREADCRUMB_DEPTH) {
            trail.add(new BreadcrumbEntry(current.getId(), current.getName()));
            if (current.getParentId() == null || current.getParentId().isBlank()) {
                break;
            }
            current = folderRepository.findById(current.getParentId()).orElse(null);
            depth++;
        }
        Collections.reverse(trail);
        return trail;
    }
}
