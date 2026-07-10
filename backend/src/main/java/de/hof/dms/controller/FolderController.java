package de.hof.dms.controller;

import de.hof.dms.dto.CreateFolderRequest;
import de.hof.dms.dto.FolderPage;
import de.hof.dms.dto.FolderResponse;
import de.hof.dms.dto.FolderSummary;
import de.hof.dms.dto.FolderViewResponse;
import de.hof.dms.dto.UpdateFolderRequest;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.FolderService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for browsing and managing the folder hierarchy under {@code /api/folders}.
 *
 * <p>Requires an authenticated user; per-folder visibility and mutation are enforced by
 * {@link FolderService} against the resolved {@link CurrentUser}. Only active when a MongoDB
 * backend is present (profile {@code !no-mongo}).
 */
@RestController
@RequestMapping("/api/folders")
@Profile("!no-mongo")
public class FolderController {

    private final FolderService folderService;

    /**
     * Creates the controller with the folder service it delegates to.
     *
     * @param folderService the service handling folder operations and access checks
     */
    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /**
     * Lists the child folders of a given parent (or top-level folders when none is given),
     * filtered to those visible to the current user.
     *
     * @param parentId optional parent folder id; null lists top-level folders
     * @param page zero-based page index
     * @param size page size
     * @param jwt the current user's JWT
     * @return a page of folders visible to the user
     */
    @GetMapping
    public FolderPage list(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return folderService.listFolders(parentId, page, size, CurrentUser.fromJwt(jwt));
    }

    /**
     * Creates a new folder on behalf of the current user, returning HTTP 201 on success.
     *
     * @param request the folder creation payload (name, parent, etc.)
     * @param jwt the current user's JWT
     * @return a 201 response containing the created folder
     */
    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @RequestBody CreateFolderRequest request, @AuthenticationPrincipal Jwt jwt) {
        FolderResponse created = folderService.createFolder(request, CurrentUser.fromJwt(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Applies a partial update (e.g. rename or move) to the given folder if the current
     * user is permitted.
     *
     * @param id the folder id
     * @param request the fields to update
     * @param jwt the current user's JWT
     * @return the updated folder
     */
    @PatchMapping("/{id}")
    public FolderResponse update(
            @PathVariable String id,
            @RequestBody UpdateFolderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return folderService.updateFolder(id, request, CurrentUser.fromJwt(jwt));
    }

    /**
     * Deletes the given folder, returning HTTP 204. When {@code recursive} is false a
     * non-empty folder is rejected by the service; when true its contents are removed too.
     *
     * @param id the folder id
     * @param recursive whether to delete the folder together with its contents
     * @param jwt the current user's JWT
     * @return a 204 no-content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive,
            @AuthenticationPrincipal Jwt jwt) {
        folderService.deleteFolder(id, recursive, CurrentUser.fromJwt(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns a flat list summarizing every folder visible to the current user, suitable
     * for building a navigation tree on the client.
     *
     * @param jwt the current user's JWT
     * @return summaries of all folders visible to the user
     */
    @GetMapping("/tree")
    public List<FolderSummary> tree(@AuthenticationPrincipal Jwt jwt) {
        return folderService.listAllFolders(CurrentUser.fromJwt(jwt));
    }

    /**
     * Returns the root-level view (subfolders and documents) for the current user.
     *
     * @param jwt the current user's JWT
     * @return the root folder view
     */
    @GetMapping("/root")
    public FolderViewResponse root(@AuthenticationPrincipal Jwt jwt) {
        return folderService.getRootView(CurrentUser.fromJwt(jwt));
    }

    /**
     * Returns the contents view (subfolders and documents) of a specific folder for the
     * current user.
     *
     * @param id the folder id
     * @param jwt the current user's JWT
     * @return the folder view
     */
    @GetMapping("/{id}")
    public FolderViewResponse folder(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return folderService.getView(id, CurrentUser.fromJwt(jwt));
    }
}
