package com.aucontraire.gmailbuddy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for SecurityLogUtil to ensure secure token masking
 * and prevent sensitive data exposure in logs.
 *
 * These tests verify that:
 * - Tokens are properly masked showing only first 4 and last 4 characters
 * - Null and empty tokens are handled safely
 * - Various token formats are supported (OAuth2, Bearer, JWT-style)
 * - No sensitive data is exposed in masked output
 * - Short tokens are completely masked for security
 *
 * @author Gmail Buddy Team
 * @since Sprint 2
 */
class SecurityLogUtilTest {

    @Test
    void constructor_ShouldThrowAssertionError() {
        // Given & When & Then
        assertThrows(AssertionError.class, () -> {
            try {
                Constructor<SecurityLogUtil> constructor = SecurityLogUtil.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                constructor.newInstance();
            } catch (Exception e) {
                if (e.getCause() instanceof AssertionError) {
                    throw (AssertionError) e.getCause();
                }
                throw new RuntimeException(e);
            }
        }, "Utility class constructor should throw AssertionError");
    }

    @Test
    void maskToken_WithNullToken_ShouldReturnNullTokenPlaceholder() {
        // Given
        String token = null;

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertEquals("[NULL_TOKEN]", result);
        assertFalse(result.contains("null"), "Result should not contain 'null' string");
    }

    @Test
    void maskToken_WithEmptyToken_ShouldReturnEmptyTokenPlaceholder() {
        // Given
        String token = "";

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertEquals("[EMPTY_TOKEN]", result);
    }

    @Test
    void maskToken_WithWhitespaceOnlyToken_ShouldReturnEmptyTokenPlaceholder() {
        // Given
        String token = "   \t\n  ";

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertEquals("[EMPTY_TOKEN]", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abc", "abcd", "abcde", "abcdefgh", "abcdefghijk"})
    void maskToken_WithShortTokens_ShouldCompletelyMask(String shortToken) {
        // When
        String result = SecurityLogUtil.maskToken(shortToken);

        // Then
        assertEquals("[MASKED]", result);
        assertFalse(result.contains(shortToken), "Short token should be completely masked");
    }

    @Test
    void maskToken_WithValidOAuth2Token_ShouldMaskProperly() {
        // Given - Typical Google OAuth2 access token format
        String token = "ya29.a0AfH6SMC1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertTrue(result.startsWith("ya29"), "Should preserve first 4 characters");
        assertTrue(result.endsWith("WXYZ"), "Should preserve last 4 characters");
        assertTrue(result.contains("****"), "Should contain masking pattern");
        assertEquals("ya29****WXYZ", result);

        // Verify no sensitive data is exposed
        assertFalse(result.contains("a0AfH6SMC"), "Should not contain sensitive middle part");
        assertFalse(result.contains("1234567890"), "Should not contain sensitive data");
    }

    @Test
    void maskToken_WithJWTStyleToken_ShouldMaskProperly() {
        // Given - JWT-style token with dots
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertTrue(result.startsWith("eyJh"), "Should preserve first 4 characters");
        assertTrue(result.endsWith("sw5c"), "Should preserve last 4 characters");
        assertTrue(result.contains("****"), "Should contain masking pattern");
        assertEquals("eyJh****sw5c", result);

        // Verify JWT parts are not exposed
        assertFalse(result.contains("IkpXVCJ9"), "Should not expose header part");
        assertFalse(result.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw"), "Should not expose signature part");
    }

    @Test
    void maskToken_WithExactlyTwelveCharacters_ShouldMaskProperly() {
        // Given - Token with exactly minimum length for masking
        String token = "abcdefghijkl";

        // When
        String result = SecurityLogUtil.maskToken(token);

        // Then
        assertEquals("abcd****ijkl", result);
        assertTrue(result.startsWith("abcd"), "Should preserve first 4 characters");
        assertTrue(result.endsWith("ijkl"), "Should preserve last 4 characters");
    }

    @Test
    void maskBearerToken_WithNullToken_ShouldReturnNullTokenPlaceholder() {
        // Given
        String bearerToken = null;

        // When
        String result = SecurityLogUtil.maskBearerToken(bearerToken);

        // Then
        assertEquals("[NULL_TOKEN]", result);
    }

    @Test
    void maskBearerToken_WithEmptyToken_ShouldReturnEmptyTokenPlaceholder() {
        // Given
        String bearerToken = "";

        // When
        String result = SecurityLogUtil.maskBearerToken(bearerToken);

        // Then
        assertEquals("[EMPTY_TOKEN]", result);
    }

    @Test
    void maskBearerToken_WithBearerPrefix_ShouldPreservePrefixAndMaskToken() {
        // Given
        String bearerToken = "Bearer ya29.a0AfH6SMC1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // When
        String result = SecurityLogUtil.maskBearerToken(bearerToken);

        // Then
        assertTrue(result.startsWith("Bearer ya29"), "Should preserve Bearer prefix and first 4 token chars");
        assertTrue(result.endsWith("WXYZ"), "Should preserve last 4 token characters");
        assertEquals("Bearer ya29****WXYZ", result);

        // Verify sensitive token part is not exposed
        assertFalse(result.contains("a0AfH6SMC"), "Should not contain sensitive token part");
    }

    @Test
    void maskBearerToken_WithoutBearerPrefix_ShouldMaskAsRegularToken() {
        // Given
        String bearerToken = "ya29.a0AfH6SMC1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // When
        String result = SecurityLogUtil.maskBearerToken(bearerToken);

        // Then
        assertEquals("ya29****WXYZ", result);
        assertFalse(result.startsWith("Bearer"), "Should not add Bearer prefix if not present");
    }

    @Test
    void createSecureAuthMessage_WithValidInputs_ShouldCreateFormattedMessage() {
        // Given
        String authType = "OAuth2";
        String userId = "user@example.com";
        String token = "ya29.a0AfH6SMC1234567890abcdefghijklmnopqrstuvwxyz";
        boolean success = true;

        // When
        String result = SecurityLogUtil.createSecureAuthMessage(authType, userId, token, success);

        // Then
        String expected = "Authentication [OAuth2] for user [user@example.com] with token [ya29****wxyz] - SUCCESS";
        assertEquals(expected, result);

        // Verify no sensitive data is exposed
        assertFalse(result.contains("a0AfH6SMC"), "Should not contain sensitive token part");
        assertTrue(result.contains("ya29****wxyz"), "Should contain masked token");
    }

    @Test
    void createSecureAuthMessage_WithFailure_ShouldIndicateFailure() {
        // Given
        String authType = "Bearer";
        String userId = "api-client@service.com";
        String token = "some-api-key-token-value-12345";
        boolean success = false;

        // When
        String result = SecurityLogUtil.createSecureAuthMessage(authType, userId, token, success);

        // Then
        assertTrue(result.contains("FAILURE"), "Should indicate authentication failure");
        assertTrue(result.contains("Bearer"), "Should include auth type");
        assertTrue(result.contains("api-client@service.com"), "Should include user ID");
        assertTrue(result.contains("some****2345"), "Should contain masked token");
    }

    @ParameterizedTest
    @MethodSource("provideSensitiveParameters")
    void maskSensitiveParameter_WithSensitiveNames_ShouldMaskValues(String paramName, String paramValue, boolean shouldBeMasked) {
        // When
        String result = SecurityLogUtil.maskSensitiveParameter(paramName, paramValue);

        // Then
        if (shouldBeMasked) {
            assertNotEquals(paramValue, result, "Sensitive parameter should be masked");
            if (paramValue != null && paramValue.length() >= 12) {
                assertTrue(result.contains("****"), "Masked value should contain mask pattern");
            }
        } else {
            assertEquals(paramValue, result, "Non-sensitive parameter should not be masked");
        }
    }

    @Test
    void maskSensitiveParameter_WithNullInputs_ShouldHandleGracefully() {
        // When & Then
        assertEquals("value", SecurityLogUtil.maskSensitiveParameter(null, "value"));
        assertNull(SecurityLogUtil.maskSensitiveParameter("param", null));
        assertNull(SecurityLogUtil.maskSensitiveParameter(null, null));
    }

    @Test
    void maskSensitiveParameter_WithMixedCaseNames_ShouldDetectSensitiveParameters() {
        // Given
        String token = "sensitive-token-value-123456789";

        // When & Then
        assertNotEquals(token, SecurityLogUtil.maskSensitiveParameter("TOKEN", token));
        assertNotEquals(token, SecurityLogUtil.maskSensitiveParameter("Access_Token", token));
        assertNotEquals(token, SecurityLogUtil.maskSensitiveParameter("CLIENT_SECRET", token));
        assertNotEquals(token, SecurityLogUtil.maskSensitiveParameter("api_KEY", token));
    }

    /**
     * Provides test data for sensitive parameter testing.
     * Format: paramName, paramValue, shouldBeMasked
     */
    private static Stream<Arguments> provideSensitiveParameters() {
        String longValue = "this-is-a-long-value-that-should-be-masked-123456789";
        String shortValue = "short";

        return Stream.of(
            // Sensitive parameters that should be masked
            Arguments.of("token", longValue, true),
            Arguments.of("access_token", longValue, true),
            Arguments.of("refresh_token", longValue, true),
            Arguments.of("id_token", longValue, true),
            Arguments.of("password", longValue, true),
            Arguments.of("passwd", longValue, true),
            Arguments.of("pwd", longValue, true),
            Arguments.of("secret", longValue, true),
            Arguments.of("client_secret", longValue, true),
            Arguments.of("key", longValue, true),
            Arguments.of("api_key", longValue, true),
            Arguments.of("apikey", longValue, true),
            Arguments.of("authorization", longValue, true),
            Arguments.of("auth", longValue, true),

            // Short sensitive values (should still be masked but differently)
            Arguments.of("token", shortValue, true),
            Arguments.of("password", shortValue, true),

            // Non-sensitive parameters that should not be masked
            Arguments.of("username", longValue, false),
            Arguments.of("email", longValue, false),
            Arguments.of("name", longValue, false),
            Arguments.of("id", longValue, false),
            Arguments.of("scope", longValue, false),
            Arguments.of("redirect_uri", longValue, false),
            Arguments.of("state", longValue, false),
            Arguments.of("code", longValue, false),
            Arguments.of("grant_type", longValue, false)
        );
    }

    @Test
    void maskToken_SecurityVerification_ShouldNeverExposeFullToken() {
        // Given - Various realistic token formats
        String[] testTokens = {
            "ya29.a0AfH6SMBxw1PzR8vQ2eX9K4mL7nN0pO1qR2sT3uV4wX5yZ6aB7cD8eF9gH0iJ1kL2m",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "sk-1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKL",
            "ghp_1234567890abcdefghijklmnopqrstuvwxyz",
            "Bearer ya29.a0AfH6SMBxw1PzR8vQ2eX9K4mL7nN0pO1qR2sT3uV4wX5yZ6aB7cD8eF9gH0iJ1kL2m"
        };

        for (String token : testTokens) {
            // When
            String masked = SecurityLogUtil.maskToken(token);

            // Then - Security assertions
            assertNotEquals(token, masked, "Token should never be equal to its masked version");

            if (token.length() >= 12) {
                // For long tokens, verify masking pattern
                assertTrue(masked.contains("****"), "Long tokens should contain mask pattern");
                assertEquals(token.substring(0, 4), masked.substring(0, 4),
                           "First 4 characters should be preserved");
                assertEquals(token.substring(token.length() - 4),
                           masked.substring(masked.length() - 4),
                           "Last 4 characters should be preserved");

                // Critical: Verify no sensitive middle part is exposed
                String middlePart = token.substring(4, token.length() - 4);
                assertFalse(masked.contains(middlePart),
                           "Masked token should never contain the sensitive middle part");
            } else {
                // Short tokens should be completely masked
                assertEquals("[MASKED]", masked, "Short tokens should be completely masked");
            }
        }
    }
}