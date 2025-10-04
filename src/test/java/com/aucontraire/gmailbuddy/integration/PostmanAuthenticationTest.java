package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    @Test
    public void testPostmanBearerTokenAuthentication() {
        // Given - Mock Google token validation to return true
        when(tokenValidator.isValidGoogleToken(anyString())).thenReturn(true);

        // Simulate token info response
        GoogleTokenValidator.TokenInfoResponse tokenInfo = new GoogleTokenValidator.TokenInfoResponse();
        tokenInfo.setEmail("test@example.com");
        tokenInfo.setScope("https://www.googleapis.com/auth/gmail.readonly");
        tokenInfo.setExpiresIn("3600");
        when(tokenValidator.getTokenInfo(anyString())).thenReturn(tokenInfo);

        // When - Make API call with Bearer token (like Postman would)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("ya29.test-google-token");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "http://localhost:" + port + "/api/v1/gmail/messages";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        // Then - Should be authorized (not 401 Unauthorized)
        // Note: We expect 500 or other error due to missing Gmail service setup,
        // but NOT 401 which would indicate authentication failure
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void testPostmanWithInvalidToken() {
        // Given - Mock Google token validation to return false
        when(tokenValidator.isValidGoogleToken(anyString())).thenReturn(false);

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
        String url = "http://localhost:" + port + "/api/v1/gmail/messages";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Then - Should be unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}