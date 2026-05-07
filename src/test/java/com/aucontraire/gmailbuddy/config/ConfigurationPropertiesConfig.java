package com.aucontraire.gmailbuddy.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;

/**
 * Test configuration for configuration properties tests.
 * Provides the required Validator bean for property validation testing.
 *
 * <p>Uses {@link LocalValidatorFactoryBean} rather than
 * {@code Validation.buildDefaultValidatorFactory()} so that the produced
 * {@link Validator} is backed by Spring's {@code SpringConstraintValidatorFactory}.
 * That factory is what allows {@code ConstraintValidator} implementations such as
 * {@link com.aucontraire.gmailbuddy.validation.MaxBodySizeValidator} to receive
 * {@code @Autowired} dependencies from the Spring application context. A plain
 * Hibernate {@code ValidatorFactory} (the previous implementation) does not
 * participate in Spring's dependency-injection lifecycle, so any validator that
 * uses {@code @Autowired} field injection would see {@code null} for those fields,
 * causing a {@link NullPointerException} inside {@code isValid}.</p>
 */
@TestConfiguration
public class ConfigurationPropertiesConfig {

    @Bean
    public Validator validator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.afterPropertiesSet();
        return factory;
    }
}