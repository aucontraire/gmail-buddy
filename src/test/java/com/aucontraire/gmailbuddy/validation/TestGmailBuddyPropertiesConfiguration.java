package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestGmailBuddyPropertiesConfiguration {

    @Bean
    @Primary
    public GmailBuddyProperties gmailBuddyProperties() {
        return new GmailBuddyProperties(
            new GmailBuddyProperties.GmailApi(
                "Gmail Buddy Test",
                "me",
                50,
                100L,
                new GmailBuddyProperties.GmailApi.RateLimit(60L,
                    new GmailBuddyProperties.GmailApi.RateLimit.BatchOperations(
                        1000L, 3, 1000L, 2.0, 30000L, 50, 0L
                    )),
                new GmailBuddyProperties.GmailApi.ServiceUnavailable(60L),
                new GmailBuddyProperties.GmailApi.MessageProcessing(
                    new GmailBuddyProperties.GmailApi.MessageProcessing.MimeTypes("text/html", "text/plain"),
                    new GmailBuddyProperties.GmailApi.MessageProcessing.Labels("UNREAD")
                ),
                new GmailBuddyProperties.GmailApi.QueryOperators("from:", "to:", "subject:", "has:attachment", "label:", " AND ")
            ),
            new GmailBuddyProperties.OAuth2(
                "google",
                new GmailBuddyProperties.OAuth2.Token("Bearer ")
            ),
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
                    "INTERNAL_SERVER_ERROR"
                ),
                new GmailBuddyProperties.ErrorHandling.ErrorCategories("CLIENT_ERROR", "SERVER_ERROR")
            ),
            new GmailBuddyProperties.Validation(
                new GmailBuddyProperties.Validation.GmailQuery(
                    "<script>|javascript:|vbscript:|data:|<iframe|<object|<embed",
                    "^[a-zA-Z0-9\\s:@\\._\\-\\(\\)\\\"]+$"
                ),
                new GmailBuddyProperties.Validation.Email("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
            ),
            new GmailBuddyProperties.Security(
                new String[]{"/actuator/health", "/api/v1/auth/**"},
                new GmailBuddyProperties.Security.OAuth2Security("/dashboard", "/oauth2/authorization/google")
            ),
            new GmailBuddyProperties.Environment(
                new GmailBuddyProperties.Environment.EnvFile("src/main/resources", ".env")
            )
        );
    }
}