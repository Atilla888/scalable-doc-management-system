package de.hof.dms.controller;

<<<<<<< HEAD
import de.hof.dms.dto.CreateFolderRequest;
import de.hof.dms.dto.FolderPage;
import de.hof.dms.dto.FolderResponse;
=======
>>>>>>> 7a096b8adde8bbb5ea2abb75ab164ce3f91f3231
import de.hof.dms.dto.FolderViewResponse;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.FolderService;
import org.springframework.context.annotation.Profile;
<<<<<<< HEAD
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
=======
>>>>>>> 7a096b8adde8bbb5ea2abb75ab164ce3f91f3231
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
<<<<<<< HEAD
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
=======
import org.springframework.web.bind.annotation.RequestMapping;
>>>>>>> 7a096b8adde8bbb5ea2abb75ab164ce3f91f3231
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
@Profile("!no-mongo")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

<<<<<<< HEAD
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

=======
>>>>>>> 7a096b8adde8bbb5ea2abb75ab164ce3f91f3231
    @GetMapping("/root")
    public FolderViewResponse root(@AuthenticationPrincipal Jwt jwt) {
        return folderService.getRootView(CurrentUser.fromJwt(jwt));
    }

    @GetMapping("/{id}")
    public FolderViewResponse folder(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return folderService.getView(id, CurrentUser.fromJwt(jwt));
    }
}
