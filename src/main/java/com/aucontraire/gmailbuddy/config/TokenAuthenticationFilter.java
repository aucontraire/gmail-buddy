package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Custom authentication filter for handling Bearer tokens from API clients.
 *
 * This filter intercepts API requests with Authorization: Bearer headers,
 * validates the tokens using GoogleTokenValidator, and establishes
 * authentication context for the request.
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Phase 2 OAuth2 Security Context Decoupling
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final GoogleTokenValidator tokenValidator;

    public TokenAuthenticationFilter(GoogleTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only process API endpoints
        if (!isApiEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if authentication already exists (OAuth2 flow)
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
            SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String bearerToken = extractBearerToken(request);
        if (bearerToken != null) {
            try {
                if (tokenValidator.isValidGoogleToken(bearerToken)) {
                    // Get token info to extract user details
                    GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(bearerToken);

                    // Create authentication with user email as principal
                    String userEmail = tokenInfo.getEmail();
                    if (userEmail == null || userEmail.trim().isEmpty()) {
                        userEmail = tokenInfo.getUserId(); // Fallback to user ID
                    }
                    if (userEmail == null || userEmail.trim().isEmpty()) {
                        userEmail = "api-user"; // Final fallback
                    }

                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userEmail,
                        bearerToken, // Store the token as credentials
                        List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("Successfully authenticated API request with Bearer token for user: {}", userEmail);
                } else {
                    logger.debug("Invalid Bearer token provided in API request");
                }
            } catch (Exception e) {
                logger.debug("Bearer token authentication failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Determines if the request is targeting an API endpoint.
     *
     * @param request the HTTP request
     * @return true if this is an API endpoint request
     */
    private boolean isApiEndpoint(String requestUri) {
        return requestUri != null && requestUri.startsWith("/api/v1/");
    }

    /**
     * Determines if the request is targeting an API endpoint.
     *
     * @param request the HTTP request
     * @return true if this is an API endpoint request
     */
    private boolean isApiEndpoint(HttpServletRequest request) {
        return isApiEndpoint(request.getRequestURI());
    }

    /**
     * Extracts Bearer token from Authorization header.
     *
     * @param request the HTTP request
     * @return the Bearer token without the "Bearer " prefix, or null if not found
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        return null;
    }
}