package de.hof.dms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI 3 document that springdoc serves at {@code /v3/api-docs}
 * and renders as Swagger UI at {@code /swagger-ui.html}. Declares API metadata and
 * a global HTTP bearer (Keycloak JWT) security scheme so the UI offers an
 * "Authorize" dialog for calling protected endpoints.
 */
@Configuration
public class OpenApiConfig {

    /** Name of the bearer security scheme referenced by every operation. */
    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Builds the OpenAPI document describing the DMS REST and CMIS API.
     *
     * @return the OpenAPI definition with title, version, and JWT bearer security
     */
    @Bean
    public OpenAPI dmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DMS Backend API")
                        .version("0.0.1")
                        .description("REST and CMIS API for the central Document Management System. "
                                + "Authenticate with a Keycloak JWT bearer token."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
