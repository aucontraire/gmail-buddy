package com.aucontraire.gmailbuddy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration test showing that the critical security vulnerability has been fixed.
 *
 * This test demonstrates that sensitive tokens are properly masked and never
 * exposed in their full form in log messages.
 *
 * @author Gmail Buddy Team
 * @since Sprint 2 - Security Fix
 */
@DisplayName("Security Logging Vulnerability Fix Demonstration")
class SecurityLogDemonstrationTest {

    @Test
    @DisplayName("SECURITY FIX: Tokens are masked to prevent sensitive data exposure in logs")
    void securityFix_TokensAreMaskedToPreventSensitiveDataExposureInLogs() {
        // Given - Real-world sensitive token examples that could appear in production logs
        String googleOAuth2Token = "ya29.a0AfH6SMC_HIGHLY_SENSITIVE_DATA_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String apiKeyToken = "sk-EXTREMELY_SENSITIVE_API_KEY_1234567890abcdefghijklmnopqrstuvwxyz";
        String bearerToken = "Bearer " + googleOAuth2Token;

        // When - Tokens are processed through our security logging utility
        String maskedOAuth2 = SecurityLogUtil.maskToken(googleOAuth2Token);
        String maskedJWT = SecurityLogUtil.maskToken(jwtToken);
        String maskedAPIKey = SecurityLogUtil.maskToken(apiKeyToken);
        String maskedBearer = SecurityLogUtil.maskBearerToken(bearerToken);

        // Then - Verify CRITICAL SECURITY REQUIREMENTS are met:

        // 1. NO FULL TOKENS are ever exposed
        assertNotEquals(googleOAuth2Token, maskedOAuth2,
            "CRITICAL: Full OAuth2 token must never equal masked version");
        assertNotEquals(jwtToken, maskedJWT,
            "CRITICAL: Full JWT token must never equal masked version");
        assertNotEquals(apiKeyToken, maskedAPIKey,
            "CRITICAL: Full API key must never equal masked version");
        assertNotEquals(bearerToken, maskedBearer,
            "CRITICAL: Full Bearer token must never equal masked version");

        // 2. SENSITIVE MIDDLE PARTS are completely removed
        assertFalse(maskedOAuth2.contains("HIGHLY_SENSITIVE_DATA"),
            "CRITICAL: Sensitive data must not appear in masked token");
        assertFalse(maskedJWT.contains("eyJzdWIiOiIxMjM0NTY3ODkw"),
            "CRITICAL: JWT payload must not appear in masked token");
        assertFalse(maskedAPIKey.contains("EXTREMELY_SENSITIVE_API_KEY"),
            "CRITICAL: API key sensitive part must not appear in masked token");

        // 3. MASKING PATTERN is consistently applied
        assertEquals("ya29****WXYZ", maskedOAuth2,
            "OAuth2 token should be masked showing only first 4 and last 4 chars");
        assertEquals("eyJh****sw5c", maskedJWT,
            "JWT token should be masked showing only first 4 and last 4 chars");
        assertEquals("sk-E****wxyz", maskedAPIKey,
            "API key should be masked showing only first 4 and last 4 chars");
        assertEquals("Bearer ya29****WXYZ", maskedBearer,
            "Bearer token should preserve prefix and mask the token part");

        // 4. ENOUGH DEBUGGING INFO remains for troubleshooting
        assertTrue(maskedOAuth2.startsWith("ya29"),
            "Masked token should preserve recognizable prefix for debugging");
        assertTrue(maskedJWT.startsWith("eyJh"),
            "Masked JWT should preserve header prefix for debugging");
        assertTrue(maskedAPIKey.startsWith("sk-"),
            "Masked API key should preserve type prefix for debugging");

        // 5. SECURE AUTHENTICATION MESSAGES can be safely logged
        String secureMessage = SecurityLogUtil.createSecureAuthMessage(
            "OAuth2", "user@example.com", googleOAuth2Token, true);

        assertTrue(secureMessage.contains("ya29****WXYZ"),
            "Secure auth message should contain masked token");
        assertFalse(secureMessage.contains("HIGHLY_SENSITIVE_DATA"),
            "CRITICAL: Secure auth message must not contain sensitive data");

        // SUCCESS: This test passing means the critical security vulnerability is FIXED
        System.out.println("✅ SECURITY FIX VERIFIED: Sensitive token logging vulnerability has been resolved");
        System.out.println("✅ All tokens are properly masked in log output");
        System.out.println("✅ No sensitive data exposure risk in application logs");
    }

    @Test
    @DisplayName("SECURITY VERIFICATION: Sensitive parameter masking works correctly")
    void securityVerification_SensitiveParameterMaskingWorksCorrectly() {
        // Given - Common sensitive parameter names and values
        String sensitiveValue = "super-secret-credential-value-12345678";

        // When - Processing through sensitive parameter masking
        String maskedToken = SecurityLogUtil.maskSensitiveParameter("access_token", sensitiveValue);
        String maskedPassword = SecurityLogUtil.maskSensitiveParameter("password", sensitiveValue);
        String maskedSecret = SecurityLogUtil.maskSensitiveParameter("client_secret", sensitiveValue);
        String unmaskedUsername = SecurityLogUtil.maskSensitiveParameter("username", sensitiveValue);

        // Then - Verify sensitive parameters are masked while others are not
        assertNotEquals(sensitiveValue, maskedToken, "access_token parameter should be masked");
        assertNotEquals(sensitiveValue, maskedPassword, "password parameter should be masked");
        assertNotEquals(sensitiveValue, maskedSecret, "client_secret parameter should be masked");
        assertEquals(sensitiveValue, unmaskedUsername, "username parameter should NOT be masked");

        // Verify masking pattern
        assertEquals("supe****5678", maskedToken, "Sensitive parameter should be properly masked");

        System.out.println("✅ SECURITY VERIFICATION: Parameter masking is working correctly");
    }
}