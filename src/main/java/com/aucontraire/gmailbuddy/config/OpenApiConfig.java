package com.aucontraire.gmailbuddy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for Gmail Buddy API documentation.
 * Provides interactive API documentation at /swagger-ui.html
 * and OpenAPI spec at /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gmailBuddyOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8020")
                                .description("Local Development Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerAuthScheme())
                        .addSecuritySchemes("oauth2", oauth2Scheme())
                )
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
                .title("Gmail Buddy API")
                .version("1.0.0")
                .description("""
                        Gmail Buddy is a REST API for managing Gmail messages with OAuth2 authentication.

                        ## Features
                        - List and filter Gmail messages
                        - Delete single or bulk messages
                        - Modify message labels
                        - Mark messages as read

                        ## Authentication
                        This API uses OAuth2 with Google for authentication. You can either:
                        1. Use Bearer token authentication (get token via browser OAuth flow)
                        2. Use the OAuth2 flow directly

                        ## Rate Limiting
                        All endpoints include rate limiting headers:
                        - `X-RateLimit-Limit`: Maximum requests per window
                        - `X-RateLimit-Remaining`: Remaining requests
                        - `X-RateLimit-Reset`: Unix timestamp when limit resets
                        - `X-Gmail-Quota-Used`: Estimated Gmail API quota consumed

                        ## Response Format
                        All responses follow RFC 7807 for errors and include standardized metadata.
                        """)
                .contact(new Contact()
                        .name("Gmail Buddy Team")
                        .email("support@gmailbuddy.example.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter your OAuth2 access token obtained from Google OAuth flow");
    }

    private SecurityScheme oauth2Scheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 authentication with Google")
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl("https://accounts.google.com/o/oauth2/v2/auth")
                                .tokenUrl("https://oauth2.googleapis.com/token")
                                .scopes(new Scopes()
                                        .addString("email", "Access email address")
                                        .addString("profile", "Access profile information")
                                        .addString("https://www.googleapis.com/auth/gmail.readonly", "Read Gmail messages")
                                        .addString("https://www.googleapis.com/auth/gmail.modify", "Modify Gmail messages")
                                        .addString("https://mail.google.com/", "Full Gmail access")
                                )
                        )
                );
    }
}
