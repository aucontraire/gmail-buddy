package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.security.TokenReference;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
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
 * authentication context using the Encrypted Token Reference Pattern.
 *
 * SECURITY: This filter implements secure token storage by:
 * - Creating encrypted token references instead of storing raw tokens
 * - Using UUID-based reference IDs in Authentication credentials
 * - Automatically managing token lifecycle and expiration
 * - Preventing token exposure through SecurityContext
 *
 * @author Gmail Buddy Security Team
 * @since Sprint 2 - Security Context Decoupling (Fixed raw token vulnerability)
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final GoogleTokenValidator tokenValidator;
    private final TokenReferenceService tokenReferenceService;

    public TokenAuthenticationFilter(GoogleTokenValidator tokenValidator,
                                   TokenReferenceService tokenReferenceService) {
        this.tokenValidator = tokenValidator;
        this.tokenReferenceService = tokenReferenceService;
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
                // PERFORMANCE FIX: Single Google API call - gets token info which includes validation
                // Previously made 2 calls: isValidGoogleToken() + getTokenInfo()
                // Now makes 1 call: getTokenInfo() which validates and returns data
                GoogleTokenValidator.TokenInfoResponse tokenInfo = tokenValidator.getTokenInfo(bearerToken);

                // Validate Gmail scopes separately (no additional Google API call)
                if (!tokenValidator.hasValidGmailScopes(tokenInfo.getScope())) {
                    logger.debug("Token validation failed: missing required Gmail scopes. Scopes: {}",
                        tokenInfo.getScope());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                // Create authentication with user email as principal
                String userEmail = tokenInfo.getEmail();
                if (userEmail == null || userEmail.trim().isEmpty()) {
                    userEmail = tokenInfo.getUserId(); // Fallback to user ID
                }
                if (userEmail == null || userEmail.trim().isEmpty()) {
                    userEmail = "api-user"; // Final fallback
                }

                // SECURITY FIX: Create encrypted token reference instead of storing raw token
                TokenReference tokenReference = tokenReferenceService.createTokenReference(
                    bearerToken,
                    userEmail
                );

                // Store only the secure reference ID, not the raw token
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userEmail,
                    tokenReference.getReferenceId(), // SECURE: Store reference ID, not raw token
                    List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Successfully authenticated API request with secure token reference {} for user: {}",
                           tokenReference.getReferenceId(), userEmail);

            } catch (com.aucontraire.gmailbuddy.exception.AuthenticationException e) {
                // Token validation failed (invalid, expired, or Google API error)
                logger.debug("Bearer token authentication failed: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            } catch (Exception e) {
                // Unexpected error during authentication
                logger.debug("Unexpected error during Bearer token authentication: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
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