package com.stayease.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI documentation configuration.
 *
 * springdoc scans every @RestController at startup and builds an OpenAPI 3
 * document from the request mappings, @Valid DTOs and ResponseEntity types.
 * That document is served as JSON at /v3/api-docs and rendered as the
 * interactive Swagger UI at /swagger-ui.html (both already permitted in
 * SecurityConfig). This class only adds the things springdoc cannot infer
 * from the code:
 *
 *  1. @OpenAPIDefinition — the human-facing API metadata (title, version,
 *     description, contact) shown at the top of the Swagger UI page.
 *
 *  2. @SecurityScheme — declares that this API is protected by a JWT
 *     "Bearer" token. This is what makes the green "Authorize" button appear
 *     in Swagger UI, letting you paste a token once and have it attached to
 *     every "Try it out" request as the "Authorization: Bearer <token>"
 *     header that JwtAuthenticationFilter looks for.
 *
 *  3. A global @SecurityRequirement (the `security = ...` below) — tells
 *     Swagger UI to actually send that bearer header on every operation.
 *     The public /api/auth endpoints will show a padlock too, but they are
 *     permitAll in SecurityConfig, so calling them without a token still
 *     works — the padlock there is only cosmetic.
 *
 * Typical flow in the UI: call POST /api/auth/register or /api/auth/login,
 * copy the "token" from the response, click Authorize, paste it, then every
 * other endpoint becomes callable as that user.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "StayEase API",
                version = "v1",
                description = """
                        REST API for StayEase — a vacation-rental & short-stay property
                        management platform. Modules: identity & access (iam), property
                        listings & pricing, guest booking & reservations, check-in/out &
                        reviews (stay), housekeeping turnovers, maintenance, owner finance
                        (statements & payouts) and notifications.

                        Authentication: all endpoints except /api/auth/** require a JWT.
                        Obtain one from /api/auth/login or /api/auth/register, then click
                        'Authorize' and paste it.""",
                contact = @Contact(name = "StayEase Backend Team", email = "support@stayease.com"),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "/", description = "Current host")
        },
        // Apply the bearer scheme globally so the Authorize token is sent on
        // every operation. References the @SecurityScheme name below.
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Paste the JWT returned by /api/auth/login or /api/auth/register (no 'Bearer ' prefix needed).",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
    // No beans needed — the annotations above are the entire configuration.
}
