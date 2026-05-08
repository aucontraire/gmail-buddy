package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ValidBase64Validator}.
 *
 * <p>Each test instantiates the validator in isolation — no Spring context is required —
 * and verifies one logical behaviour. The {@link ConstraintValidatorContext} is mocked
 * because the validator never reads it.</p>
 *
 * <p>The validator uses {@code Base64.getDecoder().decode(value)} (standard Base64, not
 * URL-safe) wrapped in a try/catch for {@link IllegalArgumentException}. Tests exercise
 * the boundaries of what the standard decoder accepts and rejects.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 *
 * <p>Coverage targets (T022):</p>
 * <ul>
 *   <li>Valid standard Base64 (e.g., {@code JVBERi0xLjQK}) → {@code true}</li>
 *   <li>Strings with invalid characters → {@code false}</li>
 *   <li>Strings with wrong padding → {@code false}</li>
 *   <li>URL-safe Base64 without padding → {@code false} (standard decoder)</li>
 *   <li>{@code null} → {@code true}</li>
 * </ul>
 */
@DisplayName("ValidBase64Validator Tests")
class ValidBase64ValidatorTest {

    private ValidBase64Validator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new ValidBase64Validator();
        validator.initialize(null); // stateless; initialize is a no-op
    }

    // -------------------------------------------------------------------------
    // isValid — null (must return true: presence is @NotBlank's responsibility)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid_null_returnsTrue")
    void isValid_null_returnsTrue() {
        // Arrange: null is delegated to @NotBlank; @ValidBase64 must pass null through.

        // Act
        boolean result = validator.isValid(null, context);

        // Assert
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // isValid — valid standard Base64 strings (must return true)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid_validPdfHeaderBase64_returnsTrue")
    void isValid_validPdfHeaderBase64_returnsTrue() {
        // Arrange: "JVBERi0xLjQK" decodes to "%PDF-1.4\n" — a real, decodable payload.

        // Act
        boolean result = validator.isValid("JVBERi0xLjQK", context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid_validBase64WithPaddingChar_returnsTrue")
    void isValid_validBase64WithPaddingChar_returnsTrue() {
        // Arrange: "SGVsbG8=" decodes to "Hello" — includes one padding '=' character.

        // Act
        boolean result = validator.isValid("SGVsbG8=", context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid_validBase64WithDoublePadding_returnsTrue")
    void isValid_validBase64WithDoublePadding_returnsTrue() {
        // Arrange: "SGk=" decodes to "Hi" — includes one padding '=' character.

        // Act
        boolean result = validator.isValid("SGk=", context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid_validBase64NoPadding_returnsTrue")
    void isValid_validBase64NoPadding_returnsTrue() {
        // Arrange: "QUJD" decodes to "ABC" — no padding needed (length is multiple of 3).

        // Act
        boolean result = validator.isValid("QUJD", context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid_emptyString_returnsTrue")
    void isValid_emptyString_returnsTrue() {
        // Arrange: an empty string decodes to an empty byte array — the standard decoder
        // accepts it without throwing. (Presence is @NotBlank's responsibility.)

        // Act
        boolean result = validator.isValid("", context);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid_longValidBase64_returnsTrue")
    void isValid_longValidBase64_returnsTrue() {
        // Arrange: a multi-line Base64 block that the standard decoder accepts.
        // This simulates a real attachment payload.

        // Act
        boolean result = validator.isValid("SGVsbG8gV29ybGQhIFRoaXMgaXMgYSBsb25nZXIgc3RyaW5nLg==", context);

        // Assert
        assertThat(result).isTrue();
    }

    // -------------------------------------------------------------------------
    // isValid — invalid characters (must return false)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid_stringWithHyphenCharacter_returnsFalse")
    void isValid_stringWithHyphenCharacter_returnsFalse() {
        // Arrange: '-' is in the URL-safe Base64 alphabet but NOT in the standard alphabet.
        // The standard decoder (Base64.getDecoder()) rejects strings containing '-'.

        // Act
        boolean result = validator.isValid("SGVs-G8=", context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid_stringWithUnderscoreCharacter_returnsFalse")
    void isValid_stringWithUnderscoreCharacter_returnsFalse() {
        // Arrange: '_' is in the URL-safe Base64 alphabet but NOT in the standard alphabet.

        // Act
        boolean result = validator.isValid("SGVs_G8=", context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid_stringWithExclamationMark_returnsFalse")
    void isValid_stringWithExclamationMark_returnsFalse() {
        // Arrange: '!' is not in either Base64 alphabet.

        // Act
        boolean result = validator.isValid("not-valid-base64!!!", context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid_stringWithLineFeed_returnsFalse")
    void isValid_stringWithLineFeed_returnsFalse() {
        // Arrange: the standard decoder rejects whitespace by default (unlike MIME decoder).
        // A LF character in the middle of a Base64 string causes IllegalArgumentException.

        // Act
        boolean result = validator.isValid("SGVs\nbG8=", context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — wrong padding (must return false)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid_wrongPaddingExtraPad_returnsFalse")
    void isValid_wrongPaddingExtraPad_returnsFalse() {
        // Arrange: "QUJD==" has an incorrect number of padding characters for a 3-byte
        // input ("ABC" = 3 bytes = 4 Base64 chars, no padding needed). The extra '=='
        // makes the block length invalid.

        // Act
        boolean result = validator.isValid("QUJD==", context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid_wrongPaddingDoublePadOnTwoByteInput_returnsFalse")
    void isValid_wrongPaddingDoublePadOnTwoByteInput_returnsFalse() {
        // Arrange: "SGk==" has incorrect padding for a 2-byte input.
        // "SGk" decodes to 2 bytes ("Hi"), which requires exactly one '=' pad ("SGk=").
        // Adding a second '=' ("SGk==") creates an invalid 5-byte block that the JDK
        // standard decoder rejects with "Input byte array has incorrect ending byte".

        // Act
        boolean result = validator.isValid("SGk==", context);

        // Assert
        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // isValid — URL-safe Base64 without padding (must return false with standard decoder)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isValid_urlSafeBase64WithHyphenWithoutPadding_returnsFalse")
    void isValid_urlSafeBase64WithHyphenWithoutPadding_returnsFalse() {
        // Arrange: URL-safe Base64 uses '-' and '_' instead of '+' and '/'.
        // The standard decoder does NOT accept '-' or '_', so this must return false
        // regardless of whether the padding is present.
        // "SGVs-G8" uses '-' which the standard decoder rejects.

        // Act
        boolean result = validator.isValid("SGVs-G8", context);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid_urlSafeBase64WithUnderscoreWithoutPadding_returnsFalse")
    void isValid_urlSafeBase64WithUnderscoreWithoutPadding_returnsFalse() {
        // Arrange: URL-safe Base64 without padding — the spec requires standard Base64
        // (with '+' and '/'), so this must be rejected.

        // Act
        boolean result = validator.isValid("SGVsbG8_dGVzdA", context);

        // Assert
        assertThat(result).isFalse();
    }
}
