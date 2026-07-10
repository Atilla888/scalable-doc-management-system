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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
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

/**
 * REST endpoints for the document lifecycle under {@code /api/documents}: upload, metadata
 * retrieval and update, binary download, deletion, and per-document permission management.
 *
 * <p>Requires an authenticated user; access to each document and its permissions is enforced
 * by {@link DocumentService} against the resolved {@link CurrentUser}. Only active when a
 * MongoDB backend is present (profile {@code !no-mongo}).
 */
@RestController
@RequestMapping("/api/documents")
@Profile("!no-mongo")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Creates the controller with the document service it delegates to.
     *
     * @param documentService the service handling document operations and access checks
     */
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Uploads a new document with its metadata into the target folder on behalf of the
     * current user.
     *
     * @param file the uploaded file content
     * @param title the document title
     * @param documentType the document type classifier
     * @param parentId the id of the containing folder
     * @param description optional free-text description
     * @param eapCategory optional EAP category
     * @param inheritFromParent whether the document inherits its ACL from the parent folder
     * @param jwt the current user's JWT
     * @return a 200 response describing the stored document
     */
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

    /**
     * Returns the metadata of a document the current user is allowed to read.
     *
     * @param id the document id
     * @param jwt the current user's JWT
     * @return the document metadata
     */
    @GetMapping("/{id}")
    public DocumentMetadataResponse getMetadata(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.getMetadata(id, CurrentUser.fromJwt(jwt));
    }

    /**
     * Streams the binary content of a document as a file attachment, if the current user may
     * read it. The response carries the stored content type and original file name.
     *
     * @param id the document id
     * @param jwt the current user's JWT
     * @return a 200 response with the document content as a downloadable attachment
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        DocumentService.DownloadPayload payload =
                documentService.download(id, CurrentUser.fromJwt(jwt));
        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(payload.fileName(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(payload.resource());
    }

    /**
     * Deletes a document the current user is permitted to remove, returning HTTP 204.
     *
     * @param id the document id
     * @param jwt the current user's JWT
     * @return a 204 no-content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        documentService.delete(id, CurrentUser.fromJwt(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the editable metadata of a document the current user may modify.
     *
     * @param id the document id
     * @param request the new metadata values
     * @param jwt the current user's JWT
     * @return the updated document metadata
     */
    @PutMapping("/{id}/metadata")
    public DocumentMetadataResponse updateMetadata(
            @PathVariable String id,
            @RequestBody MetadataUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return documentService.updateMetadata(id, request, CurrentUser.fromJwt(jwt));
    }

    /**
     * Returns the access-control settings of a document for the current user.
     *
     * @param id the document id
     * @param jwt the current user's JWT
     * @return the document's permissions
     */
    @GetMapping("/{id}/permissions")
    public PermissionsResponse getPermissions(
            @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.getPermissions(id, CurrentUser.fromJwt(jwt));
    }

    /**
     * Replaces the access-control list of a document, if the current user is authorized to
     * manage its permissions.
     *
     * @param id the document id
     * @param request the new ACL to apply
     * @param jwt the current user's JWT
     * @return the persisted ACL
     */
    @PutMapping("/{id}/permissions")
    public AclDto updatePermissions(
            @PathVariable String id,
            @RequestBody AclDto request,
            @AuthenticationPrincipal Jwt jwt) {
        return documentService.updatePermissions(id, request, CurrentUser.fromJwt(jwt));
    }
}
