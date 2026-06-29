package de.hof.dms.controller;

import de.hof.dms.dto.AdminOverviewResponse;
import de.hof.dms.service.AdminOverviewService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Profile("!no-mongo")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminOverviewService.getOverview();
    }
}
