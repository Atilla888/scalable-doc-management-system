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

@RestController
@RequestMapping("/api/admin/ocr")
@Profile("!no-mongo")
public class AdminOcrController {

    private final OcrAdminService ocrAdminService;

    public AdminOcrController(OcrAdminService ocrAdminService) {
        this.ocrAdminService = ocrAdminService;
    }

    @GetMapping
    public List<OcrJobResponse> listJobs(
            @RequestParam(value = "status", required = false) String status) {
        return ocrAdminService.listJobs(status);
    }

    @PostMapping("/{id}/retry")
    public OcrJobResponse retry(@PathVariable String id) {
        return ocrAdminService.retry(id);
    }
}
