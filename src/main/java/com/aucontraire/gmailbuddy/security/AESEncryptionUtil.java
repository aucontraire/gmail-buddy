package com.aucontraire.gmailbuddy.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility for secure token storage.
 *
 * This utility provides secure encryption and decryption of OAuth2 tokens
 * using AES-256 encryption in GCM mode for authenticated encryption.
 *
 * Security Features:
 * - AES-256-GCM authenticated encryption
 * - Random IV generation for each encryption
 * - Configurable encryption key from properties
 * - Secure memory handling
 *
 * @author Gmail Buddy Security Team
 * @since Sprint 2 - Security Context Decoupling
 */
@Component
public class AESEncryptionUtil {

    private static final Logger logger = LoggerFactory.getLogger(AESEncryptionUtil.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * Constructor that initializes encryption with a configured key.
     *
     * @param encryptionKey Base64-encoded encryption key from configuration
     */
    public AESEncryptionUtil(@Value("${app.security.token.encryption-key:}") String encryptionKey) {
        this.secureRandom = new SecureRandom();
        this.secretKey = initializeSecretKey(encryptionKey);
        logger.info("AESEncryptionUtil initialized with secure key");
    }

    /**
     * Encrypts a token using AES-256-GCM.
     *
     * @param token the plain text token to encrypt
     * @return Base64-encoded encrypted token with IV prepended
     * @throws IllegalArgumentException if token is null or empty
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt token
            byte[] encryptedData = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

            String result = Base64.getEncoder().encodeToString(encryptedWithIv);
            logger.debug("Token encrypted successfully");
            return result;

        } catch (Exception e) {
            logger.error("Failed to encrypt token: {}", e.getMessage());
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    /**
     * Decrypts a token using AES-256-GCM.
     *
     * @param encryptedToken Base64-encoded encrypted token with IV prepended
     * @return the decrypted plain text token
     * @throws IllegalArgumentException if encryptedToken is null or empty
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted token cannot be null or empty");
        }

        try {
            // Decode from Base64
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedToken);

            if (encryptedWithIv.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted token format");
            }

            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
            System.arraycopy(encryptedWithIv, iv.length, encryptedData, 0, encryptedData.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt token
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, StandardCharsets.UTF_8);

            logger.debug("Token decrypted successfully");
            return result;

        } catch (Exception e) {
            logger.error("Failed to decrypt token: {}", e.getMessage());
            throw new RuntimeException("Token decryption failed", e);
        }
    }

    /**
     * Initializes the secret key from configuration or generates a new one.
     *
     * @param configuredKey Base64-encoded key from configuration
     * @return SecretKey for AES encryption
     */
    private SecretKey initializeSecretKey(String configuredKey) {
        try {
            if (configuredKey != null && !configuredKey.trim().isEmpty()) {
                // Use configured key
                byte[] keyBytes = Base64.getDecoder().decode(configuredKey.trim());
                if (keyBytes.length != 32) { // 256 bits
                    throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes)");
                }
                logger.info("Using configured encryption key");
                return new SecretKeySpec(keyBytes, ALGORITHM);
            } else {
                // Generate new key for development
                logger.warn("No encryption key configured. Generating temporary key for session. " +
                           "Configure 'app.security.token.encryption-key' for production use.");
                KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                keyGenerator.init(256);
                return keyGenerator.generateKey();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES algorithm not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption key", e);
        }
    }

    /**
     * Generates a new Base64-encoded AES-256 key for configuration.
     * This method is for development/setup purposes only.
     *
     * @return Base64-encoded 256-bit AES key
     */
    public static String generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES algorithm not available", e);
        }
    }
}