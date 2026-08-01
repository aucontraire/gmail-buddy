package com.aucontraire.gmailbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.unit.DataSize;

/**
 * Tests for {@link GoogleTransportConfig} (WI-2/US1): a single shared, pooled, validated
 * {@link HttpTransport} for all Gmail API calls.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleTransportConfig Tests")
class GoogleTransportConfigTest {

    private static final int MAX_PER_ROUTE = 16;
    private static final int MAX_TOTAL = 20;
    private static final long VALIDATE_AFTER_INACTIVITY_MS = 2000L;
    private static final long CONNECTION_TTL_MS = 60000L;

    @Mock
    private GmailBuddyProperties properties;

    @Mock
    private GmailBuddyProperties.GmailApi gmailApi;

    private GmailBuddyProperties.GmailApi.HttpTransport httpTransportConfig;

    @BeforeEach
    void setUp() {
        httpTransportConfig = new GmailBuddyProperties.GmailApi.HttpTransport(
                MAX_PER_ROUTE, MAX_TOTAL, VALIDATE_AFTER_INACTIVITY_MS, CONNECTION_TTL_MS);

        lenient().when(properties.gmailApi()).thenReturn(gmailApi);
        lenient().when(gmailApi.httpTransport()).thenReturn(httpTransportConfig);
    }

    @Test
    @DisplayName("httpTransport() bean is an ApacheHttpTransport")
    void httpTransportBeanIsApacheHttpTransport() {
        HttpTransport transport = new GoogleTransportConfig(properties).httpTransport();

        assertThat(transport).isInstanceOf(ApacheHttpTransport.class);
    }

    @Test
    @DisplayName("connection pool is configured from GmailBuddyProperties.httpTransport()")
    void connectionPoolReflectsConfiguredValues() {
        PoolingHttpClientConnectionManager connectionManager =
                new GoogleTransportConfig(properties).createConnectionManager();

        assertThat(connectionManager.getMaxTotal()).isEqualTo(MAX_TOTAL);
        assertThat(connectionManager.getDefaultMaxPerRoute()).isEqualTo(MAX_PER_ROUTE);
        assertThat(connectionManager.getDefaultMaxPerRoute()).isGreaterThanOrEqualTo(16);
        assertThat(connectionManager.getValidateAfterInactivity()).isEqualTo((int) VALIDATE_AFTER_INACTIVITY_MS);
    }

    @Test
    @DisplayName("httpTransport() bean is a singleton within the Spring application context")
    void httpTransportBeanIsSingletonInApplicationContext() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(GoogleTransportConfig.class, TestPropertiesConfig.class)) {
            HttpTransport first = context.getBean(HttpTransport.class);
            HttpTransport second = context.getBean(HttpTransport.class);

            assertThat(first).isInstanceOf(ApacheHttpTransport.class);
            assertThat(first).isSameAs(second);
        }
    }

    /** Supplies a real (non-mocked) {@link GmailBuddyProperties} for the Spring-context test. */
    @Configuration
    static class TestPropertiesConfig {
        @Bean
        @Primary
        GmailBuddyProperties gmailBuddyProperties() {
            return new GmailBuddyProperties(
                    new GmailBuddyProperties.GmailApi(
                            "Gmail Buddy Test",
                            "me",
                            50,
                            100L,
                            1000L,
                            new GmailBuddyProperties.GmailApi.RateLimit(
                                    60L,
                                    new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                                            1000L, 3, 1000L, 2.0, 30000L, 50, 0L)),
                            new GmailBuddyProperties.GmailApi.ServiceUnavailable(60L),
                            new GmailBuddyProperties.GmailApi.MessageProcessing(
                                    new GmailBuddyProperties.GmailApi.MessageProcessing.MimeTypes(
                                            "text/html", "text/plain"),
                                    new GmailBuddyProperties.GmailApi.MessageProcessing.Labels("UNREAD")),
                            new GmailBuddyProperties.GmailApi.QueryOperators(
                                    "from:", "to:", "subject:", "has:attachment", "label:", " AND "),
                            new GmailBuddyProperties.GmailApi.HttpTransport(
                                    MAX_PER_ROUTE, MAX_TOTAL, VALIDATE_AFTER_INACTIVITY_MS, CONNECTION_TTL_MS),
                            new GmailBuddyProperties.GmailApi.TokenValidationCache(true, 60)),
                    new GmailBuddyProperties.OAuth2("google", new GmailBuddyProperties.OAuth2.Token("Bearer ")),
                    new GmailBuddyProperties.ErrorHandling(
                            new GmailBuddyProperties.ErrorHandling.ErrorCodes(
                                    "RATE_LIMIT_EXCEEDED",
                                    "SERVICE_UNAVAILABLE",
                                    "VALIDATION_ERROR",
                                    "CONSTRAINT_VIOLATION",
                                    "GMAIL_SERVICE_ERROR",
                                    "MESSAGE_NOT_FOUND",
                                    "AUTHENTICATION_ERROR",
                                    "AUTHORIZATION_ERROR",
                                    "RESOURCE_NOT_FOUND",
                                    "GMAIL_API_ERROR",
                                    "INTERNAL_SERVER_ERROR"),
                            new GmailBuddyProperties.ErrorHandling.ErrorCategories("CLIENT_ERROR", "SERVER_ERROR")),
                    new GmailBuddyProperties.Validation(
                            new GmailBuddyProperties.Validation.GmailQuery(
                                    "<script>|javascript:|vbscript:|data:|<iframe|<object|<embed",
                                    "^[a-zA-Z0-9\\s:@\\._\\-\\(\\)\\\"]+$"),
                            new GmailBuddyProperties.Validation.Email(
                                    "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")),
                    new GmailBuddyProperties.Security(
                            new String[] {"/actuator/health", "/api/v1/auth/**"},
                            new GmailBuddyProperties.Security.OAuth2Security(
                                    "/dashboard", "/oauth2/authorization/google")),
                    new GmailBuddyProperties.Environment(
                            new GmailBuddyProperties.Environment.EnvFile("src/main/resources", ".env")),
                    new GmailBuddyProperties.ApplicationRateLimit(1000, 60),
                    new GmailBuddyProperties.Send(DataSize.ofMegabytes(10), 500, 998, DataSize.ofMegabytes(25)));
        }
    }
}
