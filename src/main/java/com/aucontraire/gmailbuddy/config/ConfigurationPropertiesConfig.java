package com.aucontraire.gmailbuddy.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to enable and register configuration properties.
 * This makes GmailBuddyProperties available as a Spring bean.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(GmailBuddyProperties.class)
@ConfigurationPropertiesScan(basePackages = "com.aucontraire.gmailbuddy.config")
public class ConfigurationPropertiesConfig {
    // This class enables automatic registration of @ConfigurationProperties
    // classes and makes them available for dependency injection
}