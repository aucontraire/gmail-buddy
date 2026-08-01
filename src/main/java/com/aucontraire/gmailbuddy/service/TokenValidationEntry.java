package com.aucontraire.gmailbuddy.service;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of one successful Google tokeninfo validation, memoized so rapid
 * same-token requests within a short TTL window don't each pay a live round-trip to
 * Google (WI-2/US3).
 *
 * <p>Invariants (see {@code specs/007-transport-token-efficiency/data-model.md}):
 * <ul>
 *   <li>Never contains the raw token or any PII beyond what {@link GoogleTokenValidator.TokenInfoResponse}
 *       already carries (email/scope) — the cache map is keyed by a SHA-256 hash of the
 *       raw token, never by the raw token itself (FR-008).</li>
 *   <li>Only created for successful validations (FR-006).</li>
 *   <li>Never honored past {@code effectiveExpiry}, which is capped at the token's own
 *       Google-reported expiry regardless of the configured memo TTL (FR-007).</li>
 *   <li>The wrapped {@link GoogleTokenValidator.TokenInfoResponse} retains its scope, so a
 *       cache hit remains subject to the same scope-enforcement gate as a live validation
 *       (FR-012) — this record does not shortcut that check.</li>
 * </ul>
 *
 * @author Gmail Buddy Team
 * @since WI-2 - Transport/token efficiency
 */
public record TokenValidationEntry(
        GoogleTokenValidator.TokenInfoResponse tokenInfo,
        Instant cachedAt,
        Instant googleExpiresAt,
        Instant effectiveExpiry) {

    public TokenValidationEntry {
        Objects.requireNonNull(tokenInfo, "tokenInfo cannot be null");
        Objects.requireNonNull(cachedAt, "cachedAt cannot be null");
        Objects.requireNonNull(googleExpiresAt, "googleExpiresAt cannot be null");
        Objects.requireNonNull(effectiveExpiry, "effectiveExpiry cannot be null");
    }

    /**
     * Whether this memo entry is no longer valid to serve as of {@code now}.
     *
     * @param now the current time
     * @return true if {@code now} is at or past {@link #effectiveExpiry()}
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(effectiveExpiry);
    }
}
