package com.aucontraire.gmailbuddy.client;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Client for creating Gmail API service instances with OAuth2 authentication.
 * This component provides a factory method for creating authenticated Gmail service
 * instances using Bearer token authentication.
 * 
 * @author Gmail Buddy Team
 * @since 1.0
 */
@Component
public class GmailClient {
    private static final Logger logger = LoggerFactory.getLogger(GmailClient.class);
    
    private final GmailBuddyProperties properties;
    
    @Autowired
    public GmailClient(GmailBuddyProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a Gmail service instance authenticated with the provided access token.
     * Uses Google's trusted HTTP transport and Gson JSON factory for API communication.
     * 
     * @param accessToken the OAuth2 access token for authentication
     * @return an authenticated Gmail service instance
     * @throws GeneralSecurityException if there's a security-related error creating the transport
     * @throws IOException if there's an I/O error during service creation
     */
    public Gmail createGmailService(String accessToken) throws GeneralSecurityException, IOException {
        logger.debug("Creating Gmail service with access token");
        String tokenPrefix = properties.oauth2().token().prefix();
        String applicationName = properties.gmailApi().applicationName();
        
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization(tokenPrefix + accessToken)
        ).setApplicationName(applicationName).build();
    }
}
