package de.hof.dms.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal liveness endpoint used by container and load-balancer health probes.
 *
 * <p>The {@code /health} path is publicly accessible (permitted without authentication in
 * {@code SecurityConfig}).
 */
@RestController
public class HealthController {

    /**
     * Reports that the service is up.
     *
     * @return a map containing {@code {"status": "ok"}}
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
