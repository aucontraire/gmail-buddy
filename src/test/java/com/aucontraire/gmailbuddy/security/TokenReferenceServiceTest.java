package com.aucontraire.gmailbuddy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Comprehensive security tests for TokenReferenceService.
 *
 * These tests verify secure token reference creation, storage, retrieval,
 * and lifecycle management including expiration and cleanup.
 *
 * @author Gmail Buddy Security Team
 */
class TokenReferenceServiceTest {

    @Mock
    private AESEncryptionUtil encryptionUtil;

    private TokenReferenceService tokenReferenceService;
    private static final String TEST_TOKEN = "ya29.a0ARrdaM-test-token-value-12345";
    private static final String ENCRYPTED_TOKEN = "encrypted-test-token-base64";
    private static final String TEST_USER_ID = "user@example.com";
    private static final long DEFAULT_LIFETIME = 3600; // 1 hour

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenReferenceService = new TokenReferenceService(encryptionUtil, DEFAULT_LIFETIME);

        // Setup default encryption mock behavior
        when(encryptionUtil.encrypt(anyString())).thenReturn(ENCRYPTED_TOKEN);
        when(encryptionUtil.decrypt(ENCRYPTED_TOKEN)).thenReturn(TEST_TOKEN);
    }

    @Test
    @DisplayName("Should create secure token reference successfully")
    void shouldCreateSecureTokenReferenceSuccessfully() {
        // Act
        TokenReference tokenReference = tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID);

        // Assert
        assertThat(tokenReference).isNotNull();
        assertThat(tokenReference.getReferenceId()).isNotNull();
        assertThat(tokenReference.getReferenceId()).isNotEmpty();
        assertThat(tokenReference.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(tokenReference.getEncryptedToken()).isEqualTo(ENCRYPTED_TOKEN);
        assertThat(tokenReference.isValid()).isTrue();
        assertThat(tokenReference.getSecondsUntilExpiration()).isGreaterThan(0);

        // Verify encryption was called
        verify(encryptionUtil).encrypt(TEST_TOKEN);

        // Verify active token count
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create token reference with custom lifetime")
    void shouldCreateTokenReferenceWithCustomLifetime() {
        long customLifetime = 1800; // 30 minutes

        // Act
        TokenReference tokenReference = tokenReferenceService.createTokenReference(
            TEST_TOKEN, TEST_USER_ID, customLifetime);

        // Assert
        assertThat(tokenReference.getSecondsUntilExpiration()).isLessThanOrEqualTo(customLifetime);
        assertThat(tokenReference.getSecondsUntilExpiration()).isGreaterThan(customLifetime - 5); // Allow for small delay
    }

    @Test
    @DisplayName("Should reject invalid parameters for token creation")
    void shouldRejectInvalidParametersForTokenCreation() {
        // Test null token
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(null, TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raw token cannot be null or empty");

        // Test empty token
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference("", TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raw token cannot be null or empty");

        // Test whitespace token
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference("   ", TEST_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raw token cannot be null or empty");

        // Test null user ID
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(TEST_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null or empty");

        // Test empty user ID
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(TEST_TOKEN, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null or empty");

        // Test invalid lifetime
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token lifetime must be positive");

        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID, -100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token lifetime must be positive");
    }

    @Test
    @DisplayName("Should retrieve and decrypt token successfully")
    void shouldRetrieveAndDecryptTokenSuccessfully() {
        // Arrange
        TokenReference tokenReference = tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID);

        // Act
        Optional<String> retrievedToken = tokenReferenceService.getToken(tokenReference.getReferenceId());

        // Assert
        assertThat(retrievedToken).isPresent();
        assertThat(retrievedToken.get()).isEqualTo(TEST_TOKEN);
        verify(encryptionUtil).decrypt(ENCRYPTED_TOKEN);
    }

    @Test
    @DisplayName("Should return empty for non-existent token reference")
    void shouldReturnEmptyForNonExistentTokenReference() {
        // Act
        Optional<String> result = tokenReferenceService.getToken("non-existent-reference-id");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle expired token references")
    void shouldHandleExpiredTokenReferences() {
        // Create a token reference with very short lifetime
        TokenReference tokenReference = tokenReferenceService.createTokenReference(
            TEST_TOKEN, TEST_USER_ID, 1);

        // Wait for expiration
        try {
            Thread.sleep(1100); // Wait 1.1 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        Optional<String> result = tokenReferenceService.getToken(tokenReference.getReferenceId());
        Optional<TokenReference> refResult = tokenReferenceService.getTokenReference(tokenReference.getReferenceId());

        // Assert
        assertThat(result).isEmpty();
        assertThat(refResult).isEmpty();
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(0); // Should be cleaned up
    }

    @Test
    @DisplayName("Should validate token reference correctly")
    void shouldValidateTokenReferenceCorrectly() {
        // Arrange
        TokenReference tokenReference = tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID);

        // Act & Assert - valid reference
        assertThat(tokenReferenceService.isValidReference(tokenReference.getReferenceId())).isTrue();

        // Act & Assert - invalid reference
        assertThat(tokenReferenceService.isValidReference("non-existent")).isFalse();
        assertThat(tokenReferenceService.isValidReference(null)).isFalse();
        assertThat(tokenReferenceService.isValidReference("")).isFalse();
    }

    @Test
    @DisplayName("Should remove token reference successfully")
    void shouldRemoveTokenReferenceSuccessfully() {
        // Arrange
        TokenReference tokenReference = tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID);
        String referenceId = tokenReference.getReferenceId();

        // Verify token exists
        assertThat(tokenReferenceService.isValidReference(referenceId)).isTrue();
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(1);

        // Act
        boolean removed = tokenReferenceService.removeTokenReference(referenceId);

        // Assert
        assertThat(removed).isTrue();
        assertThat(tokenReferenceService.isValidReference(referenceId)).isFalse();
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(0);

        // Try to remove again
        boolean removedAgain = tokenReferenceService.removeTokenReference(referenceId);
        assertThat(removedAgain).isFalse();
    }

    @Test
    @DisplayName("Should remove all token references for user")
    void shouldRemoveAllTokenReferencesForUser() {
        // Arrange - create multiple tokens for same user and different user
        TokenReference token1 = tokenReferenceService.createTokenReference(TEST_TOKEN + "1", TEST_USER_ID);
        TokenReference token2 = tokenReferenceService.createTokenReference(TEST_TOKEN + "2", TEST_USER_ID);
        TokenReference token3 = tokenReferenceService.createTokenReference(TEST_TOKEN + "3", "other@example.com");

        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(3);

        // Act
        int removedCount = tokenReferenceService.removeTokenReferencesForUser(TEST_USER_ID);

        // Assert
        assertThat(removedCount).isEqualTo(2);
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(1);
        assertThat(tokenReferenceService.isValidReference(token1.getReferenceId())).isFalse();
        assertThat(tokenReferenceService.isValidReference(token2.getReferenceId())).isFalse();
        assertThat(tokenReferenceService.isValidReference(token3.getReferenceId())).isTrue();
    }

    @Test
    @DisplayName("Should provide accurate cache statistics")
    void shouldProvideAccurateCacheStatistics() {
        // Initially empty
        TokenReferenceService.TokenCacheStats stats = tokenReferenceService.getCacheStats();
        assertThat(stats.getTotalTokens()).isEqualTo(0);
        assertThat(stats.getActiveTokens()).isEqualTo(0);
        assertThat(stats.getExpiredTokens()).isEqualTo(0);

        // Add some tokens
        tokenReferenceService.createTokenReference(TEST_TOKEN + "1", TEST_USER_ID);
        tokenReferenceService.createTokenReference(TEST_TOKEN + "2", TEST_USER_ID, 1); // Expires quickly

        // Check stats before expiration
        stats = tokenReferenceService.getCacheStats();
        assertThat(stats.getTotalTokens()).isEqualTo(2);
        assertThat(stats.getActiveTokens()).isEqualTo(2);
        assertThat(stats.getExpiredTokens()).isEqualTo(0);

        // Wait for one to expire
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check stats after expiration
        stats = tokenReferenceService.getCacheStats();
        assertThat(stats.getTotalTokens()).isEqualTo(2);
        assertThat(stats.getActiveTokens()).isEqualTo(1);
        assertThat(stats.getExpiredTokens()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle encryption failures gracefully")
    void shouldHandleEncryptionFailuresGracefully() {
        // Arrange - mock encryption failure
        when(encryptionUtil.encrypt(anyString())).thenThrow(new RuntimeException("Encryption failed"));

        // Act & Assert
        assertThatThrownBy(() -> tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token reference creation failed");

        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle decryption failures gracefully")
    void shouldHandleDecryptionFailuresGracefully() {
        // Arrange
        TokenReference tokenReference = tokenReferenceService.createTokenReference(TEST_TOKEN, TEST_USER_ID);

        // Mock decryption failure
        when(encryptionUtil.decrypt(anyString())).thenThrow(new RuntimeException("Decryption failed"));

        // Act
        Optional<String> result = tokenReferenceService.getToken(tokenReference.getReferenceId());

        // Assert
        assertThat(result).isEmpty();
        // Token should be removed due to corruption
        assertThat(tokenReferenceService.isValidReference(tokenReference.getReferenceId())).isFalse();
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent access")
    void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 50;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String token = TEST_TOKEN + "-thread-" + threadId + "-op-" + j;
                    String userId = TEST_USER_ID + "-thread-" + threadId;

                    TokenReference ref = tokenReferenceService.createTokenReference(token, userId);
                    Optional<String> retrieved = tokenReferenceService.getToken(ref.getReferenceId());
                    assertThat(retrieved).isPresent();
                    tokenReferenceService.removeTokenReference(ref.getReferenceId());
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Final state should be clean
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should cleanup expired tokens automatically")
    void shouldCleanupExpiredTokensAutomatically() {
        // This test would normally test the @Scheduled method, but we'll test the logic manually
        // since scheduling is not active in unit tests

        // Create tokens with different lifetimes
        tokenReferenceService.createTokenReference(TEST_TOKEN + "1", TEST_USER_ID, 3600); // 1 hour
        tokenReferenceService.createTokenReference(TEST_TOKEN + "2", TEST_USER_ID, 1); // 1 second

        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(2);

        // Wait for short-lived token to expire
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Manually trigger cleanup (normally done by @Scheduled method)
        tokenReferenceService.cleanupExpiredTokens();

        // Should have only 1 active token left
        assertThat(tokenReferenceService.getActiveTokenCount()).isEqualTo(1);
    }
}