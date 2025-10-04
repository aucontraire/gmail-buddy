package com.aucontraire.gmailbuddy.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Secure token reference entity for the Encrypted Token Reference Pattern.
 *
 * This class represents a secure reference to an encrypted OAuth2 token,
 * providing token lifecycle management without exposing raw tokens
 * in Spring Security contexts.
 *
 * Security Features:
 * - Unique UUID-based reference IDs
 * - Encrypted token storage
 * - Automatic expiration handling
 * - User association for audit trail
 * - Immutable design for thread safety
 *
 * @author Gmail Buddy Security Team
 * @since Sprint 2 - Security Context Decoupling
 */
public class TokenReference {

    private final String referenceId;
    private final String encryptedToken;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String tokenType;
    private final String scope;

    /**
     * Private constructor for builder pattern.
     */
    private TokenReference(Builder builder) {
        this.referenceId = builder.referenceId;
        this.encryptedToken = builder.encryptedToken;
        this.userId = builder.userId;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
        this.tokenType = builder.tokenType;
        this.scope = builder.scope;
    }

    /**
     * Creates a new TokenReference builder.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the unique reference ID for this token.
     *
     * @return UUID-based reference ID
     */
    public String getReferenceId() {
        return referenceId;
    }

    /**
     * Gets the encrypted token data.
     *
     * @return AES-256 encrypted token
     */
    public String getEncryptedToken() {
        return encryptedToken;
    }

    /**
     * Gets the user ID associated with this token.
     *
     * @return user identifier (email or user ID)
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return when this token reference was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the expiration timestamp.
     *
     * @return when this token reference expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Gets the token type (e.g., "Bearer").
     *
     * @return token type
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Gets the OAuth2 scope associated with this token.
     *
     * @return OAuth2 scope string
     */
    public String getScope() {
        return scope;
    }

    /**
     * Checks if this token reference has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this token reference is valid (not expired).
     *
     * @return true if valid, false if expired
     */
    public boolean isValid() {
        return !isExpired();
    }

    /**
     * Gets the remaining time until expiration in seconds.
     *
     * @return seconds until expiration, 0 if already expired
     */
    public long getSecondsUntilExpiration() {
        Instant now = Instant.now();
        if (now.isAfter(expiresAt)) {
            return 0;
        }
        return expiresAt.getEpochSecond() - now.getEpochSecond();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenReference that = (TokenReference) o;
        return Objects.equals(referenceId, that.referenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceId);
    }

    @Override
    public String toString() {
        return "TokenReference{" +
                "referenceId='" + referenceId + '\'' +
                ", userId='" + userId + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", tokenType='" + tokenType + '\'' +
                ", scope='" + scope + '\'' +
                ", expired=" + isExpired() +
                '}';
    }

    /**
     * Builder class for creating TokenReference instances.
     */
    public static class Builder {
        private String referenceId;
        private String encryptedToken;
        private String userId;
        private Instant createdAt;
        private Instant expiresAt;
        private String tokenType;
        private String scope;

        private Builder() {
            this.referenceId = UUID.randomUUID().toString();
            this.createdAt = Instant.now();
            this.tokenType = "Bearer";
        }

        /**
         * Sets a custom reference ID (optional - UUID generated by default).
         *
         * @param referenceId custom reference ID
         * @return this builder
         */
        public Builder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        /**
         * Sets the encrypted token (required).
         *
         * @param encryptedToken AES-256 encrypted token
         * @return this builder
         */
        public Builder encryptedToken(String encryptedToken) {
            this.encryptedToken = encryptedToken;
            return this;
        }

        /**
         * Sets the user ID (required).
         *
         * @param userId user identifier
         * @return this builder
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets a custom creation time (optional - current time by default).
         *
         * @param createdAt creation timestamp
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the expiration time (required).
         *
         * @param expiresAt expiration timestamp
         * @return this builder
         */
        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /**
         * Sets expiration duration from now (alternative to expiresAt).
         *
         * @param durationSeconds seconds until expiration
         * @return this builder
         */
        public Builder expiresIn(long durationSeconds) {
            this.expiresAt = Instant.now().plusSeconds(durationSeconds);
            return this;
        }

        /**
         * Sets the token type (optional - "Bearer" by default).
         *
         * @param tokenType token type
         * @return this builder
         */
        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        /**
         * Sets the OAuth2 scope (optional).
         *
         * @param scope OAuth2 scope
         * @return this builder
         */
        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Builds the TokenReference instance.
         *
         * @return new TokenReference
         * @throws IllegalStateException if required fields are missing
         */
        public TokenReference build() {
            if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
                throw new IllegalStateException("Encrypted token is required");
            }
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalStateException("User ID is required");
            }
            if (expiresAt == null) {
                throw new IllegalStateException("Expiration time is required");
            }
            if (createdAt.isAfter(expiresAt)) {
                throw new IllegalStateException("Expiration time must be after creation time");
            }

            return new TokenReference(this);
        }
    }
}