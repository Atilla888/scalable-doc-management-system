package de.hof.dms.service;

import de.hof.dms.domain.Folder;
import de.hof.dms.dto.BreadcrumbEntry;
import de.hof.dms.dto.DocumentSummary;
import de.hof.dms.dto.FolderSummary;
import de.hof.dms.dto.FolderViewResponse;
import de.hof.dms.exception.ApiException;
import de.hof.dms.repository.DocumentRepository;
import de.hof.dms.repository.FolderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Profile("!no-mongo")
public class FolderService {

    private static final String ROOT_PATH = "/";
    private static final int MAX_BREADCRUMB_DEPTH = 64;

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
