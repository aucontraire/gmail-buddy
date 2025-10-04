package com.aucontraire.gmailbuddy.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing secure token references and their lifecycle.
 *
 * This service implements the Encrypted Token Reference Pattern by providing
 * secure storage, retrieval, and lifecycle management of OAuth2 tokens without
 * exposing raw tokens in Spring Security contexts.
 *
 * Features:
 * - Thread-safe in-memory token storage
 * - Automatic token expiration and cleanup
 * - Token usage tracking and audit
 * - Configurable token lifetime
 * - Memory-efficient token management
 *
 * @author Gmail Buddy Security Team
 * @since Sprint 2 - Security Context Decoupling
 */
@Service
public class TokenReferenceService {

    private static final Logger logger = LoggerFactory.getLogger(TokenReferenceService.class);

    private final ConcurrentHashMap<String, TokenReference> tokenCache;
    private final AESEncryptionUtil encryptionUtil;
    private final long defaultTokenLifetimeSeconds;
    private final AtomicInteger activeTokenCount;

    /**
     * Constructor with configurable token lifetime.
     *
     * @param encryptionUtil encryption utility for token security
     * @param defaultTokenLifetimeSeconds default token lifetime from configuration
     */
    public TokenReferenceService(
            AESEncryptionUtil encryptionUtil,
            @Value("${app.security.token.default-lifetime-seconds:3600}") long defaultTokenLifetimeSeconds) {
        this.tokenCache = new ConcurrentHashMap<>();
        this.encryptionUtil = encryptionUtil;
        this.defaultTokenLifetimeSeconds = defaultTokenLifetimeSeconds;
        this.activeTokenCount = new AtomicInteger(0);
        logger.info("TokenReferenceService initialized with {}s default token lifetime",
                   defaultTokenLifetimeSeconds);
    }

    /**
     * Creates a secure token reference from a raw OAuth2 token.
     *
     * @param rawToken the raw OAuth2 access token
     * @param userId the user identifier (email or user ID)
     * @return TokenReference containing encrypted token and metadata
     * @throws IllegalArgumentException if parameters are invalid
     * @throws RuntimeException if encryption fails
     */
    public TokenReference createTokenReference(String rawToken, String userId) {
        return createTokenReference(rawToken, userId, defaultTokenLifetimeSeconds);
    }

    /**
     * Creates a secure token reference with custom lifetime.
     *
     * @param rawToken the raw OAuth2 access token
     * @param userId the user identifier (email or user ID)
     * @param lifetimeSeconds token lifetime in seconds
     * @return TokenReference containing encrypted token and metadata
     * @throws IllegalArgumentException if parameters are invalid
     * @throws RuntimeException if encryption fails
     */
    public TokenReference createTokenReference(String rawToken, String userId, long lifetimeSeconds) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw token cannot be null or empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (lifetimeSeconds <= 0) {
            throw new IllegalArgumentException("Token lifetime must be positive");
        }

        try {
            // Encrypt the raw token
            String encryptedToken = encryptionUtil.encrypt(rawToken);

            // Create token reference
            TokenReference tokenReference = TokenReference.builder()
                    .encryptedToken(encryptedToken)
                    .userId(userId)
                    .expiresIn(lifetimeSeconds)
                    .scope("gmail.readonly gmail.modify") // Default Gmail scopes
                    .build();

            // Store in cache
            tokenCache.put(tokenReference.getReferenceId(), tokenReference);
            activeTokenCount.incrementAndGet();

            logger.debug("Created token reference {} for user {} with {}s lifetime",
                        tokenReference.getReferenceId(), userId, lifetimeSeconds);
            return tokenReference;

        } catch (Exception e) {
            logger.error("Failed to create token reference for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Token reference creation failed", e);
        }
    }

    /**
     * Retrieves and decrypts a token by its reference ID.
     *
     * @param referenceId the token reference ID
     * @return decrypted raw token, or empty if reference not found or expired
     */
    public Optional<String> getToken(String referenceId) {
        if (referenceId == null || referenceId.trim().isEmpty()) {
            return Optional.empty();
        }

        TokenReference tokenReference = tokenCache.get(referenceId);
        if (tokenReference == null) {
            logger.debug("Token reference {} not found", referenceId);
            return Optional.empty();
        }

        if (tokenReference.isExpired()) {
            logger.debug("Token reference {} has expired", referenceId);
            removeTokenReference(referenceId);
            return Optional.empty();
        }

        try {
            String decryptedToken = encryptionUtil.decrypt(tokenReference.getEncryptedToken());
            logger.debug("Successfully retrieved token for reference {}", referenceId);
            return Optional.of(decryptedToken);
        } catch (Exception e) {
            logger.error("Failed to decrypt token for reference {}: {}", referenceId, e.getMessage());
            removeTokenReference(referenceId); // Remove corrupted token
            return Optional.empty();
        }
    }

    /**
     * Gets token reference metadata without decrypting the token.
     *
     * @param referenceId the token reference ID
     * @return TokenReference metadata, or empty if not found or expired
     */
    public Optional<TokenReference> getTokenReference(String referenceId) {
        if (referenceId == null || referenceId.trim().isEmpty()) {
            return Optional.empty();
        }

        TokenReference tokenReference = tokenCache.get(referenceId);
        if (tokenReference == null) {
            return Optional.empty();
        }

        if (tokenReference.isExpired()) {
            removeTokenReference(referenceId);
            return Optional.empty();
        }

        return Optional.of(tokenReference);
    }

    /**
     * Checks if a token reference exists and is valid.
     *
     * @param referenceId the token reference ID
     * @return true if reference exists and is not expired
     */
    public boolean isValidReference(String referenceId) {
        return getTokenReference(referenceId).isPresent();
    }

    /**
     * Removes a token reference from the cache.
     *
     * @param referenceId the token reference ID to remove
     * @return true if reference was removed, false if not found
     */
    public boolean removeTokenReference(String referenceId) {
        if (referenceId == null || referenceId.trim().isEmpty()) {
            return false;
        }

        TokenReference removed = tokenCache.remove(referenceId);
        if (removed != null) {
            activeTokenCount.decrementAndGet();
            logger.debug("Removed token reference {} for user {}", referenceId, removed.getUserId());
            return true;
        }
        return false;
    }

    /**
     * Removes all token references for a specific user.
     *
     * @param userId the user identifier
     * @return number of token references removed
     */
    public int removeTokenReferencesForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }

        int removedCount = 0;
        var iterator = tokenCache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (userId.equals(entry.getValue().getUserId())) {
                iterator.remove();
                removedCount++;
                activeTokenCount.decrementAndGet();
            }
        }

        if (removedCount > 0) {
            logger.debug("Removed {} token references for user {}", removedCount, userId);
        }
        return removedCount;
    }

    /**
     * Gets the current number of active (non-expired) token references.
     *
     * @return number of active token references
     */
    public int getActiveTokenCount() {
        return activeTokenCount.get();
    }

    /**
     * Gets cache statistics for monitoring.
     *
     * @return TokenCacheStats with current cache metrics
     */
    public TokenCacheStats getCacheStats() {
        int total = tokenCache.size();
        int expired = 0;

        for (TokenReference ref : tokenCache.values()) {
            if (ref.isExpired()) {
                expired++;
            }
        }

        return new TokenCacheStats(total, total - expired, expired);
    }

    /**
     * Scheduled cleanup task to remove expired token references.
     * Runs every 5 minutes to maintain cache efficiency.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredTokens() {
        int removedCount = 0;
        var iterator = tokenCache.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removedCount++;
                activeTokenCount.decrementAndGet();
            }
        }

        if (removedCount > 0) {
            logger.info("Cleaned up {} expired token references. Active tokens: {}",
                       removedCount, activeTokenCount.get());
        }
    }

    /**
     * Cache statistics data class.
     */
    public static class TokenCacheStats {
        private final int totalTokens;
        private final int activeTokens;
        private final int expiredTokens;

        public TokenCacheStats(int totalTokens, int activeTokens, int expiredTokens) {
            this.totalTokens = totalTokens;
            this.activeTokens = activeTokens;
            this.expiredTokens = expiredTokens;
        }

        public int getTotalTokens() { return totalTokens; }
        public int getActiveTokens() { return activeTokens; }
        public int getExpiredTokens() { return expiredTokens; }

        @Override
        public String toString() {
            return String.format("TokenCacheStats{total=%d, active=%d, expired=%d}",
                               totalTokens, activeTokens, expiredTokens);
        }
    }
}