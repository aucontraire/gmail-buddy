package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NoHeaderInjectionValidator}.
 *
 * <p>Each test instantiates the validator in isolation — no Spring context is
 * required — and verifies one logical behaviour. The {@link ConstraintValidatorContext}
 * is mocked because the validator never reads it; it is only part of the API
 * signature mandated by the Bean Validation specification.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
class NoHeaderInjectionValidatorTest {

    private NoHeaderInjectionValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new NoHeaderInjectionValidator();
    }

    // -------------------------------------------------------------------------
    // isValid — null and empty inputs
    // -------------------------------------------------------------------------

    @Test
    void isValid_nullValue_returnsTrue() {
        // Arrange: presence enforcement is @NotBlank's responsibility, not this validator's.
        // A null value must be treated as valid so the two annotations compose cleanly.

        // Act
        boolean result = validator.isValid(null, context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isValid_emptyString_returnsTrue() {
        // Arrange: an empty string contains no injection characters.

        // Act
        boolean result = validator.isValid("", context);

        // Assert
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // isValid — clean strings (no injection characters)
    // -------------------------------------------------------------------------

    @Test
    void isValid_cleanString_returnsTrue() {
        // Arrange
        String cleanSubject = "Software Engineer – Application Follow-up";

        // Act
        boolean result = validator.isValid(cleanSubject, context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isValid_cleanEmailAddress_returnsTrue() {
        // Arrange
        String email = "recruiter@example.com";

        // Act
        boolean result = validator.isValid(email, context);

        // Assert
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // isValid — carriage return (\r) only
    // -------------------------------------------------------------------------

    @Test
    void isValid_carriageReturnOnly_returnsFalse() {
        // Arrange: a standalone \r (U+000D) is sufficient to terminate a header
        // line in some older parsers; must be rejected regardless of \n presence.
        String withCr = "Legitimate Subject\rX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withCr, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueIsOnlyCarriageReturn_returnsFalse() {
        // Arrange
        String onlyCr = "\r";

        // Act
        boolean result = validator.isValid(onlyCr, context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — line feed (\n) only
    // -------------------------------------------------------------------------

    @Test
    void isValid_lineFeedOnly_returnsFalse() {
        // Arrange: a standalone \n (U+000A) also terminates a header line.
        String withLf = "Legitimate Subject\nX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withLf, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueIsOnlyLineFeed_returnsFalse() {
        // Arrange
        String onlyLf = "\n";

        // Act
        boolean result = validator.isValid(onlyLf, context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — CRLF pair (\r\n)
    // -------------------------------------------------------------------------

    @Test
    void isValid_crlfPair_returnsFalse() {
        // Arrange: the canonical HTTP/SMTP header-injection vector uses \r\n together.
        String withCrlf = "Legitimate Subject\r\nX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withCrlf, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_subjectWithCarriageReturn_returnsFalse() {
        // Arrange: per FR-015, any \r in a header-bound field must be rejected.
        // This exercises the exact subject-injection pattern from the spec edge cases.
        String injectionAttempt = "Hello\rBcc: attacker@evil.com";

        // Act
        boolean result = validator.isValid(injectionAttempt, context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — injection character at different positions
    // -------------------------------------------------------------------------

    @Test
    void isValid_injectionCharacterAtStart_returnsFalse() {
        // Arrange: validator must catch injection regardless of position.
        String startsWithLf = "\nInjected";

        // Act
        boolean result = validator.isValid(startsWithLf, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_injectionCharacterAtEnd_returnsFalse() {
        // Arrange
        String endsWithCr = "Normal text\r";

        // Act
        boolean result = validator.isValid(endsWithCr, context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — additional Unicode line-terminator characters (defence-in-depth)
    // -------------------------------------------------------------------------

    @Test
    void isValid_valueWithNextLineCharacter_returnsFalse() {
        // Arrange: U+0085 NEXT LINE is a Unicode line terminator recognised by the
        // Java Language Specification and various parsers; must be rejected as
        // defence-in-depth even though current call sites encode it via RFC 2047.
        String withNel = "SubjectX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withNel, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueWithVerticalTab_returnsFalse() {
        // Arrange: U+000B VERTICAL TAB is a line terminator per the Unicode Standard
        // and the Java Language Specification; must be rejected as defence-in-depth.
        String withVt = "SubjectX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withVt, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueWithFormFeed_returnsFalse() {
        // Arrange: U+000C FORM FEED is a line terminator per the Unicode Standard
        // and the Java Language Specification; must be rejected as defence-in-depth.
        String withFf = "SubjectX-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withFf, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueWithUnicodeLineSeparator_returnsFalse() {
        // Arrange: U+2028 LINE SEPARATOR is the Unicode-native line terminator;
        // some parsers treat it identically to LF and must therefore be rejected.
        String withLs = "Subject X-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withLs, context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void isValid_valueWithUnicodeParagraphSeparator_returnsFalse() {
        // Arrange: U+2029 PARAGRAPH SEPARATOR is a Unicode line terminator that
        // some parsers treat as a block boundary; must be rejected as defence-in-depth.
        String withPs = "Subject X-Injected-Header: malicious";

        // Act
        boolean result = validator.isValid(withPs, context);

        // Assert
        assertThat(result).isFalse();
    }
}
