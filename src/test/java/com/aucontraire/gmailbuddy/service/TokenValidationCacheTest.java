package com.aucontraire.gmailbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TokenValidationCacheImpl} (WI-2/US3): the short-TTL memo of successful
 * tokeninfo validations.
 *
 * Covers: hit/miss behavior, the {@code min(configured TTL, token expiry)} ceiling, positive-only
 * caching (failures are never memoized), that the map is keyed by a SHA-256 hash and never by the
 * raw token, and that a cache hit still carries the validated scope so the caller's
 * scope-enforcement gate (FR-012) is never bypassed.
 */
@DisplayName("TokenValidationCacheImpl")
class TokenValidationCacheTest {

    private static final String TOKEN_A = "ya29.a0ARrdaM-token-A-realistic-length-example-value";
    private static final String TOKEN_B = "ya29.a0ARrdaM-token-B-realistic-length-example-value";
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private TokenValidationCacheImpl newCache(boolean enabled, int ttlSeconds) {
        GmailBuddyProperties properties = mock(GmailBuddyProperties.class);
        GmailBuddyProperties.GmailApi gmailApi = mock(GmailBuddyProperties.GmailApi.class);
        GmailBuddyProperties.GmailApi.TokenValidationCache config =
                new GmailBuddyProperties.GmailApi.TokenValidationCache(enabled, ttlSeconds);
        when(properties.gmailApi()).thenReturn(gmailApi);
        when(gmailApi.tokenValidationCache()).thenReturn(config);
        return new TokenValidationCacheImpl(properties);
    }

    private GoogleTokenValidator.TokenInfoResponse tokenInfo(String scope, String expiresIn) {
        GoogleTokenValidator.TokenInfoResponse info = new GoogleTokenValidator.TokenInfoResponse();
        info.setScope(scope);
        info.setEmail("user@example.com");
        info.setUserId("123456789");
        info.setAudience("test-client-id");
        info.setAccessType("offline");
        info.setExpiresIn(expiresIn);
        return info;
    }

    @SuppressWarnings("unchecked")
    private Map<String, TokenValidationEntry> internalMap(TokenValidationCacheImpl cache) throws Exception {
        Field field = TokenValidationCacheImpl.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (Map<String, TokenValidationEntry>) field.get(cache);
    }

    private String sha256Hex(String raw) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    @Nested
    @DisplayName("Hit/miss behavior")
    class HitMissBehaviorTests {

        @Test
        @DisplayName("Should return empty on a miss (nothing ever put)")
        void shouldReturnEmptyOnMiss() {
            TokenValidationCacheImpl cache = newCache(true, 60);

            Optional<GoogleTokenValidator.TokenInfoResponse> result = cache.get(TOKEN_A);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return the cached TokenInfoResponse on a hit")
        void shouldReturnCachedTokenInfoOnHit() {
            TokenValidationCacheImpl cache = newCache(true, 60);
            GoogleTokenValidator.TokenInfoResponse info = tokenInfo(GMAIL_READONLY_SCOPE, "3600");

            cache.put(TOKEN_A, info);
            Optional<GoogleTokenValidator.TokenInfoResponse> result = cache.get(TOKEN_A);

            assertThat(result).isPresent();
            assertThat(result.get().getScope()).isEqualTo(GMAIL_READONLY_SCOPE);
            assertThat(result.get().getEmail()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("Should miss for a different token than the one cached")
        void shouldMissForDifferentToken() {
            TokenValidationCacheImpl cache = newCache(true, 60);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            Optional<GoogleTokenValidator.TokenInfoResponse> result = cache.get(TOKEN_B);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should always miss when the cache is disabled, even after a put")
        void shouldAlwaysMissWhenDisabled() throws Exception {
            TokenValidationCacheImpl cache = newCache(false, 60);

            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            assertThat(cache.get(TOKEN_A)).isEmpty();
            assertThat(internalMap(cache)).isEmpty();
        }
    }

    @Nested
    @DisplayName("TTL ceiling: effectiveExpiry = min(configured TTL, token expiry)")
    class TtlCeilingTests {

        @Test
        @DisplayName("Should remain valid immediately after put, before either expiry")
        void shouldRemainValidBeforeEitherExpiry() {
            TokenValidationCacheImpl cache = newCache(true, 60);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            assertThat(cache.get(TOKEN_A)).isPresent();
        }

        @Test
        @DisplayName("Should expire at the configured TTL when it is shorter than the token's own expiry")
        void shouldExpireAtConfiguredTtlWhenShorterThanTokenExpiry() throws InterruptedException {
            // Configured TTL (1s) is shorter than the token's remaining validity (3600s).
            TokenValidationCacheImpl cache = newCache(true, 1);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            assertThat(cache.get(TOKEN_A)).isPresent();

            Thread.sleep(1100);

            assertThat(cache.get(TOKEN_A)).isEmpty();
        }

        @Test
        @DisplayName("Should expire at the token's own expiry when it is shorter than the configured TTL")
        void shouldExpireAtTokenExpiryWhenShorterThanConfiguredTtl() throws InterruptedException {
            // Configured TTL (60s) is longer than the token's remaining validity (1s) - the memo
            // must never be honored past the token's own expiry (FR-007), even though the
            // configured window has not elapsed.
            TokenValidationCacheImpl cache = newCache(true, 60);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "1"));

            assertThat(cache.get(TOKEN_A)).isPresent();

            Thread.sleep(1100);

            assertThat(cache.get(TOKEN_A)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Positive-only caching")
    class PositiveOnlyCachingTests {

        @Test
        @DisplayName("Should not cache a null (failed) validation result")
        void shouldNotCacheNullTokenInfo() throws Exception {
            TokenValidationCacheImpl cache = newCache(true, 60);

            cache.put(TOKEN_A, null);

            assertThat(cache.get(TOKEN_A)).isEmpty();
            assertThat(internalMap(cache)).isEmpty();
        }

        @Test
        @DisplayName("Should keep re-missing on every attempt for a token that is never successfully cached")
        void shouldReMissEveryTimeForNeverCachedToken() {
            TokenValidationCacheImpl cache = newCache(true, 60);

            cache.put(TOKEN_A, null);
            assertThat(cache.get(TOKEN_A)).isEmpty();
            cache.put(TOKEN_A, null);
            assertThat(cache.get(TOKEN_A)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Key hashing / raw token never stored")
    class KeyHashingSecurityTests {

        @Test
        @DisplayName("Should key the map by SHA-256(rawToken), never by the raw token itself")
        void shouldKeyByShaHashNotRawToken() throws Exception {
            TokenValidationCacheImpl cache = newCache(true, 60);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            Map<String, TokenValidationEntry> map = internalMap(cache);

            assertThat(map).hasSize(1);
            assertThat(map).doesNotContainKey(TOKEN_A);
            assertThat(map).containsKey(sha256Hex(TOKEN_A));
        }

        @Test
        @DisplayName("Should not have any map key equal to the raw token, even among multiple entries")
        void shouldNeverStoreRawTokenAsKeyAmongMultipleEntries() throws Exception {
            TokenValidationCacheImpl cache = newCache(true, 60);
            cache.put(TOKEN_A, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));
            cache.put(TOKEN_B, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));

            Map<String, TokenValidationEntry> map = internalMap(cache);

            assertThat(map.keySet()).noneMatch(key -> key.equals(TOKEN_A) || key.equals(TOKEN_B));
            assertThat(map.keySet()).allMatch(key -> key.matches("[0-9a-f]{64}"));
        }
    }

    @Nested
    @DisplayName("Scope preserved on hit (FR-012: memo never bypasses the scope gate)")
    class ScopeGateTests {

        @Test
        @DisplayName("Should return the full validated scope on a hit, exactly as a live validation would")
        void shouldPreserveScopeOnHit() {
            TokenValidationCacheImpl cache = newCache(true, 60);
            String multiScope = GMAIL_READONLY_SCOPE + " https://www.googleapis.com/auth/userinfo.email";
            cache.put(TOKEN_A, tokenInfo(multiScope, "3600"));

            Optional<GoogleTokenValidator.TokenInfoResponse> result = cache.get(TOKEN_A);

            assertThat(result).isPresent();
            assertThat(result.get().getScope()).isEqualTo(multiScope);
        }
    }

    @Nested
    @DisplayName("Null/blank input handling")
    class NullBlankInputTests {

        @Test
        @DisplayName("Should treat null and blank tokens as misses without throwing")
        void shouldHandleNullAndBlankTokensGracefully() {
            TokenValidationCacheImpl cache = newCache(true, 60);

            assertThat(cache.get(null)).isEmpty();
            assertThat(cache.get("   ")).isEmpty();
            cache.put(null, tokenInfo(GMAIL_READONLY_SCOPE, "3600"));
            cache.put("   ", tokenInfo(GMAIL_READONLY_SCOPE, "3600"));
        }
    }
}
