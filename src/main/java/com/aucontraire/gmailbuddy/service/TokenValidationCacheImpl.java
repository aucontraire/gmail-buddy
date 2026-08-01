package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.util.SecurityLogUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * {@link ConcurrentHashMap}-backed implementation of {@link TokenValidationCache} (WI-2/US3).
 *
 * <p>Mirrors the concurrency pattern already proven by
 * {@link com.aucontraire.gmailbuddy.security.TokenReferenceService}: an app-scoped singleton
 * holding a single concurrent map, with lazy expiry-on-read rather than a separate scheduled
 * sweep. Kept as a distinct component from {@code TokenReferenceService} on purpose — that
 * service owns encrypted raw-token storage with its own (currently fixed) TTL, while this cache
 * owns a much shorter-lived, positive-only memo of the validation result.
 *
 * @author Gmail Buddy Team
 * @since WI-2 - Transport/token efficiency
 */
@Service
public class TokenValidationCacheImpl implements TokenValidationCache {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidationCacheImpl.class);
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Fallback assumed remaining lifetime used only when Google's {@code expires_in} is missing
     * or unparsable. Live validation responses always carry {@code expires_in}, so this is a
     * defensive floor, not an expected path.
     */
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L;

    private final ConcurrentHashMap<String, TokenValidationEntry> cache = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int ttlSeconds;

    public TokenValidationCacheImpl(GmailBuddyProperties properties) {
        GmailBuddyProperties.GmailApi.TokenValidationCache config =
                properties.gmailApi().tokenValidationCache();
        this.enabled = config.enabled();
        this.ttlSeconds = config.ttlSeconds();
        logger.info("TokenValidationCache initialized (enabled={}, ttlSeconds={})", enabled, ttlSeconds);
    }

    @Override
    public Optional<GoogleTokenValidator.TokenInfoResponse> get(String rawToken) {
        if (!enabled || rawToken == null || rawToken.trim().isEmpty()) {
            return Optional.empty();
        }

        String key = hash(rawToken);
        TokenValidationEntry entry = cache.get(key);
        if (entry == null) {
            logger.debug("Token validation memo miss for token [{}]", SecurityLogUtil.maskToken(key));
            return Optional.empty();
        }

        if (entry.isExpired(Instant.now())) {
            logger.debug("Token validation memo expired for token [{}]", SecurityLogUtil.maskToken(key));
            // Remove only if still the same entry to avoid racing a concurrent re-populate.
            cache.remove(key, entry);
            return Optional.empty();
        }

        logger.debug("Token validation memo hit for token [{}]", SecurityLogUtil.maskToken(key));
        return Optional.of(entry.tokenInfo());
    }

    @Override
    public void put(String rawToken, GoogleTokenValidator.TokenInfoResponse tokenInfo) {
        if (!enabled || rawToken == null || rawToken.trim().isEmpty() || tokenInfo == null) {
            // Positive-only caching: a null/failed validation is never memoized (FR-006).
            return;
        }

        Instant now = Instant.now();
        Instant googleExpiresAt = now.plusSeconds(parseExpiresIn(tokenInfo.getExpiresIn()));
        Instant configuredExpiry = now.plusSeconds(ttlSeconds);
        Instant effectiveExpiry = configuredExpiry.isBefore(googleExpiresAt) ? configuredExpiry : googleExpiresAt;

        String key = hash(rawToken);
        cache.put(key, new TokenValidationEntry(tokenInfo, now, googleExpiresAt, effectiveExpiry));
        logger.debug(
                "Token validation memo populated for token [{}], effective for {}s",
                SecurityLogUtil.maskToken(key),
                effectiveExpiry.getEpochSecond() - now.getEpochSecond());
    }

    private long parseExpiresIn(String expiresIn) {
        if (expiresIn == null || expiresIn.trim().isEmpty()) {
            return DEFAULT_TOKEN_LIFETIME_SECONDS;
        }
        try {
            long parsed = Long.parseLong(expiresIn.trim());
            return parsed > 0 ? parsed : DEFAULT_TOKEN_LIFETIME_SECONDS;
        } catch (NumberFormatException e) {
            return DEFAULT_TOKEN_LIFETIME_SECONDS;
        }
    }

    /**
     * Hashes the raw token with SHA-256 so it is never stored or logged in reversible form
     * (FR-008).
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a standard JVM-guaranteed algorithm; unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
