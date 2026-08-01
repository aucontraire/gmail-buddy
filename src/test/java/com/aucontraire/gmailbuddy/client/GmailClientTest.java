package com.aucontraire.gmailbuddy.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.services.gmail.Gmail;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link GmailClient} (WI-2/US1): verifies the shared, pooled {@link HttpTransport} is
 * reused across {@code createGmailService} calls, and that the OAuth2 access token remains
 * per-request/per-caller under that shared transport (no cross-request token leakage, FR-002).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailClient Tests")
class GmailClientTest {

    private static final String TOKEN_PREFIX = "Bearer";

    @Mock
    private GmailBuddyProperties properties;

    @Mock
    private GmailBuddyProperties.GmailApi gmailApi;

    @Mock
    private GmailBuddyProperties.OAuth2 oauth2;

    @Mock
    private GmailBuddyProperties.OAuth2.Token token;

    /** A real (non-mocked) shared transport, matching what GoogleTransportConfig injects in production. */
    private HttpTransport sharedTransport;

    private GmailClient gmailClient;

    @BeforeEach
    void setUp() {
        lenient().when(properties.gmailApi()).thenReturn(gmailApi);
        lenient().when(gmailApi.applicationName()).thenReturn("Gmail Buddy Test");
        lenient().when(properties.oauth2()).thenReturn(oauth2);
        lenient().when(oauth2.token()).thenReturn(token);
        lenient().when(token.prefix()).thenReturn(TOKEN_PREFIX);

        sharedTransport = new ApacheHttpTransport(HttpClients.createDefault());
        gmailClient = new GmailClient(properties, sharedTransport);
    }

    @Test
    @DisplayName("createGmailService reuses the same injected HttpTransport instance across calls")
    void createGmailServiceReusesSharedTransport() throws GeneralSecurityException, IOException {
        Gmail first = gmailClient.createGmailService("token-a");
        Gmail second = gmailClient.createGmailService("token-b");

        HttpTransport firstTransport = first.getRequestFactory().getTransport();
        HttpTransport secondTransport = second.getRequestFactory().getTransport();

        assertThat(firstTransport).isSameAs(sharedTransport);
        assertThat(secondTransport).isSameAs(sharedTransport);
        assertThat(firstTransport).isSameAs(secondTransport);
    }

    @Test
    @DisplayName("two different tokens produce Gmail instances that each carry only their own token")
    void createGmailServiceIsolatesTokensPerCaller() throws GeneralSecurityException, IOException {
        Gmail serviceA = gmailClient.createGmailService("token-a");
        Gmail serviceB = gmailClient.createGmailService("token-b");

        HttpRequest requestA =
                serviceA.getRequestFactory().buildGetRequest(new GenericUrl("https://www.googleapis.com/"));
        HttpRequest requestB =
                serviceB.getRequestFactory().buildGetRequest(new GenericUrl("https://www.googleapis.com/"));

        assertThat(requestA.getHeaders().getAuthorization()).isEqualTo(TOKEN_PREFIX + " token-a");
        assertThat(requestB.getHeaders().getAuthorization()).isEqualTo(TOKEN_PREFIX + " token-b");

        // Building/inspecting requestB must not have mutated requestA's already-applied header
        // (no shared token state leaking through the shared transport).
        assertThat(requestA.getHeaders().getAuthorization()).isEqualTo(TOKEN_PREFIX + " token-a");
    }

    @Test
    @DisplayName("createGmailService applies the configured application name")
    void createGmailServiceAppliesApplicationName() throws GeneralSecurityException, IOException {
        Gmail gmail = gmailClient.createGmailService("token-a");

        assertThat(gmail.getApplicationName()).isEqualTo("Gmail Buddy Test");
    }
}
