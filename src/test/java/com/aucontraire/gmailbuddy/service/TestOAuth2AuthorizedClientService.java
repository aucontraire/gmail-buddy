package com.aucontraire.gmailbuddy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Test implementation of {@link OAuth2AuthorizedClientService} for unit testing.
 * Provides an in-memory store for OAuth2 authorized clients without requiring
 * external dependencies or database connections.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
public class TestOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private static final Logger logger = LoggerFactory.getLogger(TestOAuth2AuthorizedClientService.class);
    private final Map<String, OAuth2AuthorizedClient> clients = new HashMap<>();

    /**
     * Loads an authorized client by client registration ID and principal name.
     * 
     * @param clientRegistrationId the client registration identifier
     * @param principalName the name of the principal
     * @param <T> the type of OAuth2AuthorizedClient
     * @return the authorized client or null if not found
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        String key = clientRegistrationId + principalName;
        logger.info("Loading authorized client for key: {}", key);
        OAuth2AuthorizedClient client = clients.get(key);
        return client != null ? (T) client : null;
    }

    /**
     * Saves an authorized client for the given authentication principal.
     * 
     * @param authorizedClient the OAuth2 authorized client to save
     * @param principal the authentication principal
     */
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        String key = authorizedClient.getClientRegistration().getRegistrationId() + principal.getName();
        logger.info("Saving authorized client for key: {}", key);
        clients.put(key, authorizedClient);
    }

    /**
     * Removes an authorized client by client registration ID and principal name.
     * 
     * @param clientRegistrationId the client registration identifier
     * @param principalName the name of the principal
     */
    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        String key = clientRegistrationId + principalName;
        logger.info("Removing authorized client for key: {}", key);
        clients.remove(key);
    }

    /**
     * Utility method to add a pre-configured authorized client for testing purposes.
     * Creates a mock client registration with Google OAuth2 endpoints.
     * 
     * @param clientRegistrationId the client registration identifier
     * @param principalName the principal name
     * @param accessToken the OAuth2 access token
     */
    public void addAuthorizedClient(String clientRegistrationId, String principalName, OAuth2AccessToken accessToken) {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(clientRegistrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(clientRegistration, principalName, accessToken);
        logger.info("client: {}", client);

        logger.info("Adding authorized client for clientRegistrationId: {}, principalName: {}", clientRegistrationId, principalName);
        saveAuthorizedClient(client, new Authentication() {
            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return Collections.emptyList();
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return principalName;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                // No-op
            }

            @Override
            public String getName() {
                return principalName;
            }
        });
    }
}
