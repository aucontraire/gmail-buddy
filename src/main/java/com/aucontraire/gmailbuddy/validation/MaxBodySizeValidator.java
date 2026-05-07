package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

/**
 * Validator for the {@link MaxBodySize} constraint.
 *
 * <p>Measures the UTF-8 byte length of the supplied string and rejects it when that
 * length exceeds the limit configured in
 * {@link GmailBuddyProperties.Send#maxBodySize()}
 * ({@code gmail-buddy.send.max-body-size} in {@code application.properties},
 * defaulting to {@code 10MB}).</p>
 *
 * <p>Returns {@code true} for {@code null} values — presence enforcement is
 * delegated to {@link jakarta.validation.constraints.NotBlank}.</p>
 *
 * <h2>Injection strategy</h2>
 * <p>Spring Boot integrates Bean Validation with the Spring application context so
 * that {@code ConstraintValidator} implementations can be Spring beans. This class
 * receives {@link GmailBuddyProperties} via {@link Autowired} field injection —
 * the recommended approach documented in
 * <a href="https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html#bean-validation-spring-constraints">
 * Spring Framework reference: Spring-driven Method Validation</a> — because
 * {@code ConstraintValidator} lifecycle is managed by Hibernate Validator, which
 * asks Spring's {@code SpringConstraintValidatorFactory} to fulfill dependencies
 * after instantiation. Constructor injection is not available at that point, so
 * field-level {@code @Autowired} is the idiomatic pattern for Spring-managed
 * validators.</p>
 *
 * @see MaxBodySize
 */
public class MaxBodySizeValidator implements ConstraintValidator<MaxBodySize, String> {

    /**
     * Application configuration properties. Injected by Spring's
     * {@code SpringConstraintValidatorFactory} after Hibernate Validator
     * instantiates this class.
     */
    @Autowired
    private GmailBuddyProperties gmailBuddyProperties;

    /**
     * Initializes the validator. Configuration is read from the injected
     * {@link GmailBuddyProperties} at validation time rather than here, so
     * property changes that take effect without restart are reflected immediately.
     *
     * @param constraintAnnotation the annotation instance (unused)
     */
    @Override
    public void initialize(MaxBodySize constraintAnnotation) {
        // No annotation-level configuration; limit is sourced from GmailBuddyProperties.
    }

    /**
     * Returns {@code true} if {@code value} is {@code null} or its UTF-8 byte
     * length does not exceed {@code gmail-buddy.send.max-body-size}; {@code false}
     * otherwise.
     *
     * @param value   the message body to measure (may be {@code null})
     * @param context the constraint validator context
     * @return {@code true} when the body is within the configured size limit
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        long maxBytes = gmailBuddyProperties.send().maxBodySize().toBytes();
        long actualBytes = value.getBytes(StandardCharsets.UTF_8).length;
        return actualBytes <= maxBytes;
    }
}
