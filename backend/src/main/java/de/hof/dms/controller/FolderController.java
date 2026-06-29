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

@RestController
@RequestMapping("/api/folders")
@Profile("!no-mongo")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public FolderPage list(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return folderService.listFolders(parentId, page, size, CurrentUser.fromJwt(jwt));
    }

    @PostMapping
    public ResponseEntity<FolderResponse> create(
            @RequestBody CreateFolderRequest request, @AuthenticationPrincipal Jwt jwt) {
        FolderResponse created = folderService.createFolder(request, CurrentUser.fromJwt(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public FolderResponse update(
            @PathVariable String id,
            @RequestBody UpdateFolderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return folderService.updateFolder(id, request, CurrentUser.fromJwt(jwt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive,
            @AuthenticationPrincipal Jwt jwt) {
        folderService.deleteFolder(id, recursive, CurrentUser.fromJwt(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tree")
    public List<FolderSummary> tree(@AuthenticationPrincipal Jwt jwt) {
        return folderService.listAllFolders(CurrentUser.fromJwt(jwt));
    }

    @GetMapping("/root")
    public FolderViewResponse root(@AuthenticationPrincipal Jwt jwt) {
        return folderService.getRootView(CurrentUser.fromJwt(jwt));
    }

    @GetMapping("/{id}")
    public FolderViewResponse folder(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return folderService.getView(id, CurrentUser.fromJwt(jwt));
    }
}
