package com.aucontraire.gmailbuddy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive security tests for AESEncryptionUtil.
 *
 * These tests verify the security and functionality of token encryption/decryption
 * and ensure proper handling of edge cases and security requirements.
 *
 * @author Gmail Buddy Security Team
 */
class AESEncryptionUtilTest {

    private AESEncryptionUtil encryptionUtil;
    private static final String TEST_TOKEN = "ya29.a0ARrdaM-test-token-value-12345";
    private static final String TEST_KEY = "dGhpcy1pcy1hLTMyLWJ5dGUtdGVzdC1rZXktMTIzNDU="; // 32 bytes base64

    @BeforeEach
    void setUp() {
        encryptionUtil = new AESEncryptionUtil(TEST_KEY);
    }

    @Test
    @DisplayName("Should encrypt and decrypt token successfully")
    void shouldEncryptAndDecryptTokenSuccessfully() {
        // Act
        String encrypted = encryptionUtil.encrypt(TEST_TOKEN);
        String decrypted = encryptionUtil.decrypt(encrypted);

        // Assert
        assertThat(encrypted).isNotEqualTo(TEST_TOKEN);
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEmpty();
        assertThat(decrypted).isEqualTo(TEST_TOKEN);
    }

    @Test
    @DisplayName("Should produce different encrypted values for same token")
    void shouldProduceDifferentEncryptedValuesForSameToken() {
        // Act - encrypt same token multiple times
        String encrypted1 = encryptionUtil.encrypt(TEST_TOKEN);
        String encrypted2 = encryptionUtil.encrypt(TEST_TOKEN);
        String encrypted3 = encryptionUtil.encrypt(TEST_TOKEN);

        // Assert - each encryption should be different due to random IV
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encrypted1).isNotEqualTo(encrypted3);
        assertThat(encrypted2).isNotEqualTo(encrypted3);

        // But all should decrypt to same value
        assertThat(encryptionUtil.decrypt(encrypted1)).isEqualTo(TEST_TOKEN);
        assertThat(encryptionUtil.decrypt(encrypted2)).isEqualTo(TEST_TOKEN);
        assertThat(encryptionUtil.decrypt(encrypted3)).isEqualTo(TEST_TOKEN);
    }

    @Test
    @DisplayName("Should handle empty and null tokens with proper validation")
    void shouldHandleEmptyAndNullTokensWithProperValidation() {
        // Test null token encryption
        assertThatThrownBy(() -> encryptionUtil.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null or empty");

        // Test empty token encryption
        assertThatThrownBy(() -> encryptionUtil.encrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null or empty");

        // Test whitespace-only token encryption
        assertThatThrownBy(() -> encryptionUtil.encrypt("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token cannot be null or empty");

        // Test null token decryption
        assertThatThrownBy(() -> encryptionUtil.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encrypted token cannot be null or empty");

        // Test empty token decryption
        assertThatThrownBy(() -> encryptionUtil.decrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encrypted token cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle invalid encrypted data gracefully")
    void shouldHandleInvalidEncryptedDataGracefully() {
        // Test invalid Base64
        assertThatThrownBy(() -> encryptionUtil.decrypt("invalid-base64!@#$"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token decryption failed");

        // Test valid Base64 but invalid encrypted data
        String invalidEncrypted = Base64.getEncoder().encodeToString("invalid-encrypted-data".getBytes());
        assertThatThrownBy(() -> encryptionUtil.decrypt(invalidEncrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token decryption failed");

        // Test too short encrypted data (missing IV)
        String tooShort = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> encryptionUtil.decrypt(tooShort))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token decryption failed");
    }

    @Test
    @DisplayName("Should work with various token formats")
    void shouldWorkWithVariousTokenFormats() {
        String[] testTokens = {
                "ya29.a0ARrdaM-short",
                "ya29.a0ARrdaM-very-long-token-with-many-characters-and-special-symbols-!@#$%^&*()_+-=[]{}|;:,.<>?",
                "simple-token",
                "token.with.dots",
                "token-with-dashes",
                "token_with_underscores",
                "1234567890",
                "αβγδε", // Unicode characters
                "token with spaces",
                "multi\nline\ntoken"
        };

        for (String token : testTokens) {
            String encrypted = encryptionUtil.encrypt(token);
            String decrypted = encryptionUtil.decrypt(encrypted);
            assertThat(decrypted).isEqualTo(token);
        }
    }

    @Test
    @DisplayName("Should handle very long tokens")
    void shouldHandleVeryLongTokens() {
        // Create a very long token (10KB)
        StringBuilder longToken = new StringBuilder();
        for (int i = 0; i < 10240; i++) {
            longToken.append("a");
        }

        String token = longToken.toString();
        String encrypted = encryptionUtil.encrypt(token);
        String decrypted = encryptionUtil.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(token);
        assertThat(encrypted).isNotEqualTo(token);
    }

    @Test
    @DisplayName("Should initialize with configured key properly")
    void shouldInitializeWithConfiguredKeyProperly() {
        // Test with valid configured key
        AESEncryptionUtil utilWithKey = new AESEncryptionUtil(TEST_KEY);
        String encrypted = utilWithKey.encrypt(TEST_TOKEN);
        String decrypted = utilWithKey.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(TEST_TOKEN);

        // Test with empty key (should generate temporary key)
        AESEncryptionUtil utilWithoutKey = new AESEncryptionUtil("");
        String encrypted2 = utilWithoutKey.encrypt(TEST_TOKEN);
        String decrypted2 = utilWithoutKey.decrypt(encrypted2);
        assertThat(decrypted2).isEqualTo(TEST_TOKEN);

        // Different instances with different keys should not be compatible
        assertThatThrownBy(() -> utilWithKey.decrypt(encrypted2))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> utilWithoutKey.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should reject invalid key configurations")
    void shouldRejectInvalidKeyConfigurations() {
        // Test with invalid key length
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> new AESEncryptionUtil(shortKey))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Encryption key must be 256 bits");

        // Test with invalid Base64
        assertThatThrownBy(() -> new AESEncryptionUtil("invalid-base64!@#$"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to initialize encryption key");
    }

    @Test
    @DisplayName("Should generate valid new encryption keys")
    void shouldGenerateValidNewEncryptionKeys() {
        String newKey = AESEncryptionUtil.generateNewKey();

        assertThat(newKey).isNotNull();
        assertThat(newKey).isNotEmpty();

        // Should be valid Base64
        assertThatCode(() -> Base64.getDecoder().decode(newKey))
                .doesNotThrowAnyException();

        // Should be 32 bytes (256 bits)
        byte[] keyBytes = Base64.getDecoder().decode(newKey);
        assertThat(keyBytes).hasSize(32);

        // Should work with AESEncryptionUtil
        AESEncryptionUtil util = new AESEncryptionUtil(newKey);
        String encrypted = util.encrypt(TEST_TOKEN);
        String decrypted = util.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(TEST_TOKEN);
    }

    @Test
    @DisplayName("Should maintain security properties under concurrent access")
    void shouldMaintainSecurityPropertiesUnderConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String token = TEST_TOKEN + "-thread-" + threadId + "-op-" + j;
                    String encrypted = encryptionUtil.encrypt(token);
                    String decrypted = encryptionUtil.decrypt(encrypted);
                    assertThat(decrypted).isEqualTo(token);
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
    }
}