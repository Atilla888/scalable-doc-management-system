package de.hof.dms.controller;

import de.hof.dms.dto.AdminOverviewResponse;
import de.hof.dms.service.AdminOverviewService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST endpoint exposing aggregate system statistics under {@code /api/admin}.
 *
 * <p>Access is restricted to users with the {@code dms_admin} realm role via the URL-based
 * authorization rules in {@code SecurityConfig}. Only active when a MongoDB backend is
 * present (profile {@code !no-mongo}).
 */
@RestController
@RequestMapping("/api/admin")
@Profile("!no-mongo")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    /**
     * Returns an aggregate overview of the system (e.g. counts and status summaries)
     * for the admin dashboard. Requires the {@code dms_admin} role.
     *
     * @return the admin overview payload
     */
    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminOverviewService.getOverview();
    }
}
