package com.aucontraire.gmailbuddy.config;

import com.aucontraire.gmailbuddy.service.TestTokenProvider;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides a TestTokenProvider for integration tests.
 * 
 * This configuration replaces the OAuth2TokenProvider with a test implementation
 * that doesn't require actual OAuth2 authentication, making integration tests
 * simpler and more reliable.
 */
@TestConfiguration
public class TestTokenProviderConfiguration {
    
    @Bean
    @Primary
    public TokenProvider testTokenProvider() {
        return new TestTokenProvider();
    }
}