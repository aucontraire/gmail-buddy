package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.unit.DataSize;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaxBodySizeValidator}.
 *
 * <h2>Injection approach</h2>
 * <p>{@link MaxBodySizeValidator} uses {@code @Autowired} field injection of
 * {@link GmailBuddyProperties} because Hibernate Validator manages the
 * validator lifecycle (it cannot receive constructor arguments). To keep this
 * test a pure unit test with zero Spring context overhead, the
 * {@code gmailBuddyProperties} field is set via reflection in {@link #setUp()}.
 * A hand-rolled {@link GmailBuddyProperties} stub is constructed directly using
 * the record canonical constructor — no mocking of the properties object itself.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
class MaxBodySizeValidatorTest {

    /** 10 MB in bytes — matches the production default for gmail-buddy.send.max-body-size. */
    private static final long TEN_MB_BYTES = 10L * 1024L * 1024L;

    private MaxBodySizeValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        validator = new MaxBodySizeValidator();

        // Inject a hand-rolled GmailBuddyProperties with max-body-size=10MB via
        // reflection. This avoids a Spring context while exercising the real
        // validator logic against a realistic configuration object.
        GmailBuddyProperties properties = buildTestProperties(DataSize.ofBytes(TEN_MB_BYTES));
        injectProperties(validator, properties);
    }

    // -------------------------------------------------------------------------
    // isValid — null and presence
    // -------------------------------------------------------------------------

    @Test
    void isValid_nullBody_returnsTrue() {
        // Arrange: presence enforcement is @NotBlank's responsibility.
        // The validator must return true for null so annotations compose cleanly.

        // Act
        boolean result = validator.isValid(null, context);

        // Assert
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // isValid — size boundaries (ASCII / single-byte UTF-8)
    // -------------------------------------------------------------------------

    @Test
    void isValid_bodyWellBelowLimit_returnsTrue() {
        // Arrange: a typical short plain-text email body.
        String shortBody = "Hi there,\n\nI wanted to follow up.\n\nBest regards";

        // Act
        boolean result = validator.isValid(shortBody, context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isValid_bodyAtExactLimit_returnsTrue() {
        // Arrange: 10 MB of ASCII 'A' characters.  Each 'A' is exactly 1 UTF-8
        // byte, so character count equals byte count — limit is exactly hit.
        String exactLimitBody = "A".repeat((int) TEN_MB_BYTES);

        // Act
        boolean result = validator.isValid(exactLimitBody, context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isValid_bodyOneByteAboveLimit_returnsFalse() {
        // Arrange: 10 MB + 1 ASCII byte — just over the ceiling.
        String overLimitBody = "A".repeat((int) TEN_MB_BYTES + 1);

        // Act
        boolean result = validator.isValid(overLimitBody, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_bodySignificantlyAboveLimit_returnsFalse() {
        // Arrange: the same oversized body produced by SendMessageRequestFixtures.
        // 1 KiB unit × 10 241 = 10 241 KiB > 10 240 KiB (= 10 MB).
        String oversizedBody = "A".repeat(1024).repeat(10_241);

        // Act
        boolean result = validator.isValid(oversizedBody, context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — UTF-8 multi-byte character handling
    // -------------------------------------------------------------------------

    @Test
    void isValid_multiBytUtf8CharsExceedLimitByByteCount_returnsFalse() {
        // Arrange: use a 4-byte UTF-8 emoji (U+1F600 GRINNING FACE).
        // Each emoji occupies 4 bytes in UTF-8 but only 2 Java chars (a surrogate pair).
        // With a tiny 10-byte limit, 3 emojis = 12 bytes > 10 bytes, but only 6 chars.
        // This verifies the validator is byte-aware, not character-aware.
        String emoji = "😀"; // U+1F600 — 4 UTF-8 bytes, 2 Java chars
        // 3 emojis = 12 UTF-8 bytes, which exceeds a 10-byte limit
        String threeEmojis = emoji.repeat(3);

        MaxBodySizeValidator tinyLimitValidator = new MaxBodySizeValidator();
        GmailBuddyProperties tinyProps = buildTestProperties(DataSize.ofBytes(10));
        injectPropertiesUnchecked(tinyLimitValidator, tinyProps);

        // Sanity: confirm char count < byte count for this string
        assertThat((long) threeEmojis.length()).isLessThan(12L);

        // Act
        boolean result = tinyLimitValidator.isValid(threeEmojis, context);

        // Assert: must be rejected on byte count (12 bytes > 10 byte limit),
        // even though character count (6 chars) is below 10.
        assertThat(result).isFalse();
    }

    @Test
    void isValid_multiBytUtf8CharsWithinLimitByByteCount_returnsTrue() {
        // Arrange: 2 emojis = 8 UTF-8 bytes; within a 10-byte limit.
        String emoji = "😀"; // U+1F600 — 4 UTF-8 bytes, 2 Java chars
        String twoEmojis = emoji.repeat(2); // 8 bytes, 4 Java chars

        MaxBodySizeValidator tinyLimitValidator = new MaxBodySizeValidator();
        GmailBuddyProperties tinyProps = buildTestProperties(DataSize.ofBytes(10));
        injectPropertiesUnchecked(tinyLimitValidator, tinyProps);

        // Act
        boolean result = tinyLimitValidator.isValid(twoEmojis, context);

        // Assert: 8 bytes <= 10-byte limit — must be accepted.
        assertThat(result).isTrue();
    }

    @Test
    void isValid_nonAsciiMultiByteString_byteCountEnforced_returnsFalse() {
        // Arrange: Japanese characters each occupy 3 UTF-8 bytes.
        // 4 chars × 3 bytes = 12 bytes > 10-byte limit.
        String fourJapanese = "日本語テ"; // 4 × 3 UTF-8 bytes = 12 bytes; 4 Java chars

        MaxBodySizeValidator tinyLimitValidator = new MaxBodySizeValidator();
        GmailBuddyProperties tinyProps = buildTestProperties(DataSize.ofBytes(10));
        injectPropertiesUnchecked(tinyLimitValidator, tinyProps);

        // Act
        boolean result = tinyLimitValidator.isValid(fourJapanese, context);

        // Assert: 12 bytes > 10 — must be rejected.
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@link GmailBuddyProperties} with the given
     * {@code maxBodySize}. All other nested records carry placeholder values that
     * satisfy their constraints; they are not exercised by these tests.
     */
    private static GmailBuddyProperties buildTestProperties(DataSize maxBodySize) {
        return new GmailBuddyProperties(
                new GmailBuddyProperties.GmailApi(
                        "Test App",
                        "me",
                        50,
                        100L,
                        new GmailBuddyProperties.GmailApi.RateLimit(60L,
                                new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                                        1000L, 3, 1000L, 2.0, 30000L, 50, 0L)),
                        new GmailBuddyProperties.GmailApi.ServiceUnavailable(60L),
                        new GmailBuddyProperties.GmailApi.MessageProcessing(
                                new GmailBuddyProperties.GmailApi.MessageProcessing.MimeTypes(
                                        "text/html", "text/plain"),
                                new GmailBuddyProperties.GmailApi.MessageProcessing.Labels("UNREAD")),
                        new GmailBuddyProperties.GmailApi.QueryOperators(
                                "from:", "to:", "subject:", "has:attachment", "label:", " AND ")),
                new GmailBuddyProperties.OAuth2(
                        "google",
                        new GmailBuddyProperties.OAuth2.Token("Bearer ")),
                new GmailBuddyProperties.ErrorHandling(
                        new GmailBuddyProperties.ErrorHandling.ErrorCodes(
                                "RATE_LIMIT_EXCEEDED", "SERVICE_UNAVAILABLE", "VALIDATION_ERROR",
                                "CONSTRAINT_VIOLATION", "GMAIL_SERVICE_ERROR", "MESSAGE_NOT_FOUND",
                                "AUTHENTICATION_ERROR", "AUTHORIZATION_ERROR", "RESOURCE_NOT_FOUND",
                                "GMAIL_API_ERROR", "INTERNAL_SERVER_ERROR"),
                        new GmailBuddyProperties.ErrorHandling.ErrorCategories(
                                "CLIENT_ERROR", "SERVER_ERROR")),
                new GmailBuddyProperties.Validation(
                        new GmailBuddyProperties.Validation.GmailQuery(
                                "<script>", "^[a-zA-Z0-9]+$"),
                        new GmailBuddyProperties.Validation.Email(
                                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")),
                new GmailBuddyProperties.Security(
                        new String[]{"/actuator/health"},
                        new GmailBuddyProperties.Security.OAuth2Security(
                                "/dashboard", "/oauth2/authorization/google")),
                new GmailBuddyProperties.Environment(
                        new GmailBuddyProperties.Environment.EnvFile("./", ".env")),
                new GmailBuddyProperties.ApplicationRateLimit(1000, 60),
                new GmailBuddyProperties.Send(maxBodySize, 500, 998,
                        org.springframework.util.unit.DataSize.ofMegabytes(25))
        );
    }

    /**
     * Injects {@code properties} into the {@code gmailBuddyProperties} field of
     * {@code target} via reflection, mimicking what Spring's
     * {@code SpringConstraintValidatorFactory} does at runtime.
     *
     * @throws Exception if the field cannot be accessed
     */
    private static void injectProperties(MaxBodySizeValidator target,
                                         GmailBuddyProperties properties) throws Exception {
        Field field = MaxBodySizeValidator.class.getDeclaredField("gmailBuddyProperties");
        field.setAccessible(true);
        field.set(target, properties);
    }

    /**
     * Unchecked wrapper around {@link #injectProperties} for use inside lambdas.
     */
    private static void injectPropertiesUnchecked(MaxBodySizeValidator target,
                                                   GmailBuddyProperties properties) {
        try {
            injectProperties(target, properties);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject GmailBuddyProperties via reflection", e);
        }
    }
}
