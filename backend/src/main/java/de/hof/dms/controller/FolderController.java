package de.hof.dms.controller;

import de.hof.dms.dto.FolderViewResponse;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.FolderService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
@Profile("!no-mongo")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
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
