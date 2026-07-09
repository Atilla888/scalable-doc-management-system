package de.hof.dms.controller;

import de.hof.dms.dto.AclDto;
import de.hof.dms.dto.DocumentMetadataResponse;
import de.hof.dms.dto.DocumentUploadResponse;
import de.hof.dms.dto.MetadataUpdateRequest;
import de.hof.dms.dto.PermissionsResponse;
import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.DocumentService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Profile("!no-mongo")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") String title,
            @RequestPart("documentType") String documentType,
            @RequestPart("parentId") String parentId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "eapCategory", required = false) String eapCategory,
            @RequestParam(value = "inheritFromParent", defaultValue = "true") boolean inheritFromParent,
            @AuthenticationPrincipal Jwt jwt) {
        DocumentUploadResponse response =
                documentService.upload(
                        file,
                        title,
                        documentType,
                        parentId,
                        description,
                        eapCategory,
                        inheritFromParent,
                        CurrentUser.fromJwt(jwt));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public DocumentMetadataResponse getMetadata(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.getMetadata(id, CurrentUser.fromJwt(jwt));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        DocumentService.DownloadPayload payload =
                documentService.download(id, CurrentUser.fromJwt(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + payload.fileName() + "\"")
                .body(payload.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        documentService.delete(id, CurrentUser.fromJwt(jwt));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/metadata")
    public DocumentMetadataResponse updateMetadata(
            @PathVariable String id,
            @RequestBody MetadataUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return documentService.updateMetadata(id, request, CurrentUser.fromJwt(jwt));
    }

    @GetMapping("/{id}/permissions")
    public PermissionsResponse getPermissions(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.getPermissions(id, CurrentUser.fromJwt(jwt));
    }

    @PutMapping("/{id}/permissions")
    public AclDto updatePermissions(
            @PathVariable String id,
            @RequestBody AclDto request,
            @AuthenticationPrincipal Jwt jwt) {
        return documentService.updatePermissions(id, request, CurrentUser.fromJwt(jwt));
    }
}
