package de.hof.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the DMS backend. Bootstraps component scanning,
 * auto-configuration, and the embedded web server for the whole application.
 */
@SpringBootApplication
public class DmsApplication {
    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed through to Spring
     */
    public static void main(String[] args) {
        SpringApplication.run(DmsApplication.class, args);
    }
}
