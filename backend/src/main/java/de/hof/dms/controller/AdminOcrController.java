package de.hof.dms.controller;

import de.hof.dms.dto.OcrJobResponse;
import de.hof.dms.service.OcrAdminService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin REST endpoints for inspecting and managing OCR jobs under {@code /api/admin/ocr}.
 *
 * <p>Access is restricted to users with the {@code dms_admin} realm role via the URL-based
 * authorization rules in {@code SecurityConfig}. Only active when a MongoDB backend is
 * present (profile {@code !no-mongo}).
 */
@RestController
@RequestMapping("/api/admin/ocr")
@Profile("!no-mongo")
public class AdminOcrController {

    private final OcrAdminService ocrAdminService;

    public AdminOcrController(OcrAdminService ocrAdminService) {
        this.ocrAdminService = ocrAdminService;
    }

    /**
     * Lists OCR jobs, optionally filtered by status. Requires the {@code dms_admin} role.
     *
     * @param status optional job status filter; when null all jobs are returned
     * @return the matching OCR jobs
     */
    @GetMapping
    public List<OcrJobResponse> listJobs(
            @RequestParam(value = "status", required = false) String status) {
        return ocrAdminService.listJobs(status);
    }

    /**
     * Requeues a failed or stalled OCR job for reprocessing. Requires the {@code dms_admin} role.
     *
     * @param id the identifier of the OCR job to retry
     * @return the updated job after being scheduled for retry
     */
    @PostMapping("/{id}/retry")
    public OcrJobResponse retry(@PathVariable String id) {
        return ocrAdminService.retry(id);
    }
}
