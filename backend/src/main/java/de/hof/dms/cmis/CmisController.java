package de.hof.dms.cmis;

import de.hof.dms.service.CurrentUser;
import de.hof.dms.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * CMIS 1.1 <b>Browser (JSON) binding</b> endpoint. Reads are dispatched by the
 * {@code cmisselector} query parameter (GET); writes by the {@code cmisaction}
 * form parameter (POST), exactly as the CMIS Browser binding specifies. The
 * service URL is {@code /cmis/browser}; the per-repository root URL is
 * {@code /cmis/browser/{repositoryId}/root}.
 *
 * <p>Authentication (Keycloak Bearer JWT) is enforced by the global security
 * filter chain, every {@code /cmis/**} request must be authenticated, so
 * unauthenticated calls receive {@code 401}. RBAC and CMIS faults are handled
 * inside {@link CmisService} / {@link CmisExceptionHandler}.
 */
@RestController
@RequestMapping("/cmis")
@Profile("!no-mongo")
public class CmisController {

    private final CmisService cmisService;

    public CmisController(CmisService cmisService) {
        this.cmisService = cmisService;
    }

    /** Service document: getRepositories / getRepositoryInfo for all repositories. */
    @GetMapping(value = {"/browser", "/browser/"})
    public Map<String, Object> repositories(@AuthenticationPrincipal Jwt jwt) {
        return cmisService.getRepositoryInfos();
    }

    /** Repository-level GET: repositoryInfo (default) or query. */
    @GetMapping("/browser/{repositoryId}")
    public Object repositoryService(
            @PathVariable String repositoryId,
            @RequestParam(value = "cmisselector", required = false) String selector,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "statement", required = false) String statement,
            @RequestParam(value = "maxItems", defaultValue = "0") int maxItems,
            @RequestParam(value = "skipCount", defaultValue = "0") int skipCount,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.fromJwt(jwt);
        if ("query".equalsIgnoreCase(selector)) {
            return cmisService.query(
                    repositoryId, firstNonBlank(statement, q), maxItems, skipCount, user);
        }
        return cmisService.getRepositoryInfo(repositoryId);
    }

    /** Root/object GET: object (default), children, parents, content, or query. */
    @GetMapping("/browser/{repositoryId}/root")
    public ResponseEntity<?> rootService(
            @PathVariable String repositoryId,
            @RequestParam(value = "cmisselector", required = false) String selector,
            @RequestParam(value = "objectId", required = false) String objectId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "statement", required = false) String statement,
            @RequestParam(value = "maxItems", defaultValue = "0") int maxItems,
            @RequestParam(value = "skipCount", defaultValue = "0") int skipCount,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.fromJwt(jwt);
        String sel = selector == null ? "object" : selector.toLowerCase();
        return switch (sel) {
            case "children" ->
                    ResponseEntity.ok(
                            cmisService.getChildren(repositoryId, objectId, maxItems, skipCount, user));
            case "parents" ->
                    ResponseEntity.ok(cmisService.getParents(repositoryId, objectId, user));
            case "query" ->
                    ResponseEntity.ok(
                            cmisService.query(
                                    repositoryId, firstNonBlank(statement, q), maxItems, skipCount, user));
            case "content" -> streamContent(repositoryId, objectId, user);
            case "object" -> ResponseEntity.ok(cmisService.getObject(repositoryId, objectId, user));
            default ->
                    throw new CmisException(
                            CmisFault.NOT_SUPPORTED, "Unsupported cmisselector: " + selector);
        };
    }

    /** All write operations, dispatched by cmisaction. */
    @PostMapping("/browser/{repositoryId}/root")
    public ResponseEntity<Map<String, Object>> rootAction(
            @PathVariable String repositoryId,
            @RequestParam(value = "content", required = false) MultipartFile content,
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.fromJwt(jwt);
        String action = request.getParameter("cmisaction");
        String objectId = request.getParameter("objectId");
        Map<String, String> properties = CmisObjects.parseProperties(request.getParameterMap());

        if (action == null || action.isBlank()) {
            throw new CmisException(CmisFault.INVALID_ARGUMENT, "cmisaction is required");
        }
        return switch (action.toLowerCase()) {
            case "createfolder" ->
                    ResponseEntity.status(HttpStatus.CREATED)
                            .body(cmisService.createFolder(repositoryId, objectId, properties, user));
            case "createdocument" ->
                    ResponseEntity.status(HttpStatus.CREATED)
                            .body(
                                    cmisService.createDocument(
                                            repositoryId, objectId, content, properties, user));
            case "setcontent" ->
                    ResponseEntity.ok(
                            cmisService.setContentStream(repositoryId, objectId, content, user));
            case "delete" -> {
                cmisService.deleteObject(repositoryId, objectId, user);
                yield ResponseEntity.ok(Map.of());
            }
            case "query" ->
                    ResponseEntity.ok(
                            cmisService.query(
                                    repositoryId,
                                    firstNonBlank(
                                            request.getParameter("statement"),
                                            request.getParameter("q")),
                                    intParam(request, "maxItems"),
                                    intParam(request, "skipCount"),
                                    user));
            default ->
                    throw new CmisException(
                            CmisFault.NOT_SUPPORTED, "Unsupported cmisaction: " + action);
        };
    }

    private ResponseEntity<?> streamContent(String repositoryId, String objectId, CurrentUser user) {
        DocumentService.DownloadPayload payload =
                cmisService.getContentStream(repositoryId, objectId, user);
        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(payload.fileName(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body((Resource) payload.resource());
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static int intParam(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
