package com.aucontraire.gmailbuddy.service;

import java.util.Optional;

/**
 * Short-TTL memo of successful Google tokeninfo validations (WI-2/US3).
 *
 * <p>Wraps {@link GoogleTokenValidator#getTokenInfo(String)} so a burst of rapid requests
 * carrying the same token collapses to roughly one live validation round-trip instead of
 * one per request, while preserving every security invariant of a live validation:
 * <ul>
 *   <li>Only successful validations are ever cached (FR-006).</li>
 *   <li>A hit is never honored past {@code min(configured TTL, token's own expiry)} (FR-007).</li>
 *   <li>The raw token is never stored or logged; entries are keyed by a non-reversible hash
 *       of the token (FR-008).</li>
 *   <li>Keying is per-token, not per-caller-identity (FR-009).</li>
 *   <li>A hit returns the full validated {@link GoogleTokenValidator.TokenInfoResponse}
 *       (including scope) so the caller's scope-enforcement gate still runs on every request,
 *       hit or miss — this cache never bypasses or weakens that check (FR-012).</li>
 * </ul>
 *
 * @author Gmail Buddy Team
 * @since WI-2 - Transport/token efficiency
 */
public interface TokenValidationCache {

    /**
     * Looks up a memoized validation for {@code rawToken}.
     *
     * @param rawToken the raw OAuth2 access token (hashed internally, never stored as-is)
     * @return the memoized {@link GoogleTokenValidator.TokenInfoResponse} if a non-expired
     *         entry exists, otherwise empty (cache miss, expiry, or the cache is disabled)
     */
    Optional<GoogleTokenValidator.TokenInfoResponse> get(String rawToken);

    /**
     * Memoizes a successful validation for {@code rawToken}.
     *
     * @param rawToken the raw OAuth2 access token that was just successfully validated
     *                 (hashed internally, never stored as-is)
     * @param tokenInfo the validated token info to memoize; a null value is a no-op since only
     *                  successful validations are ever cached (FR-006)
     */
    void put(String rawToken, GoogleTokenValidator.TokenInfoResponse tokenInfo);
}
