package com.aucontraire.gmailbuddy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable Spring Task Scheduling.
 *
 * This configuration enables the @Scheduled annotation support
 * for background tasks such as token cleanup in TokenReferenceService.
 *
 * @author Gmail Buddy Security Team
 * @since Sprint 2 - Security Context Decoupling
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Configuration class to enable scheduling
    // The @EnableScheduling annotation enables background task scheduling
}