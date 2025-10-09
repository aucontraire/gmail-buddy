package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration test for Postman Bearer token authentication.
 * Tests the complete flow from HTTP request to API response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PostmanAuthenticationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private GmailRepository gmailRepository;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(tokenValidator, gmailService, gmailRepository);
    }

    @Test
    public void testPostmanBearerTokenAuthentication() {
        // Given - Mock Google token validation
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("test@example.com");
        tokenInfo.setScope("https://www.googleapis.com/auth/gmail.readonly https://mail.google.com");
        tokenInfo.setExpiresIn("3600");
        when(tokenValidator.getTokenInfo(anyString())).thenReturn(tokenInfo);
        when(tokenValidator.hasValidGmailScopes(anyString())).thenReturn(true);

        // Mock Gmail service to return empty list
        List<Message> mockMessages = new ArrayList<>();
        when(gmailService.listMessages(anyString())).thenReturn(mockMessages);

        // When - Make API call with Bearer token (like Postman would)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("ya29.test-google-token");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "http://localhost:" + port + "/api/v1/gmail/messages";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        // Then - Should be authorized (200 OK)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(tokenValidator).getTokenInfo(anyString());
        verify(tokenValidator).hasValidGmailScopes(anyString());
    }

    @Test
    public void testPostmanWithInvalidToken() {
        // Given - Mock Google token validation to return null (invalid token)
        when(tokenValidator.getTokenInfo(anyString())).thenReturn(null);

        // When - Make API call with invalid Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid-token");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "http://localhost:" + port + "/api/v1/gmail/messages";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        // Then - Should be unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void testPostmanWithoutBearerToken() {
        // When - Make API call without Authorization header
        // Note: Browser-style requests without Bearer token get redirected to OAuth2 login
        String url = "http://localhost:" + port + "/api/v1/gmail/messages";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Then - Should redirect to OAuth2 login (302) or return unauthorized (401)
        // TestRestTemplate follows redirects by default, so we expect 302 FOUND
        assertThat(response.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.UNAUTHORIZED);
    }
}