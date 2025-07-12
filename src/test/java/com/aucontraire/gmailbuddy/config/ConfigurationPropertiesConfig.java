package com.aucontraire.gmailbuddy.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Test configuration for configuration properties tests.
 * Provides the required Validator bean for property validation testing.
 */
@TestConfiguration
public class ConfigurationPropertiesConfig {

    @Bean
    public Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }
}