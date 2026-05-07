package com.aucontraire.gmailbuddy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Constraint annotation that enforces a maximum message-body size measured in
 * UTF-8 bytes.
 *
 * <p>The limit is read from {@code gmail-buddy.send.max-body-size} via
 * {@link com.aucontraire.gmailbuddy.config.GmailBuddyProperties.Send#maxBodySize()}.
 * By default that property is set to {@code 10MB} in {@code application.properties},
 * aligning with Spring Boot's {@code spring.servlet.multipart.max-request-size}
 * and Gmail's practical per-message body ceiling for outreach use-cases.</p>
 *
 * <p>UTF-8 byte length — rather than Java character count — is used because Gmail
 * API accepts the raw-message byte stream. A body that fits within the character
 * limit can still exceed the byte limit when it contains multibyte Unicode
 * characters.</p>
 *
 * <p>Null values are treated as valid — presence enforcement is the responsibility
 * of {@link jakarta.validation.constraints.NotBlank}, not this constraint.</p>
 *
 * @see MaxBodySizeValidator
 */
@Documented
@Constraint(validatedBy = MaxBodySizeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBodySize {

    /**
     * Violation message returned when the body exceeds the configured byte limit.
     *
     * @return the error message template
     */
    String message() default "Message body exceeds the maximum permitted size";

    /**
     * Validation groups this constraint belongs to.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for extensibility by constraint consumers.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
