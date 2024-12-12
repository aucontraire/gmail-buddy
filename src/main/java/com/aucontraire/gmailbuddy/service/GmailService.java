package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
public class GmailService {

    private final GmailClient gmailClient;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final Logger logger = LoggerFactory.getLogger(GmailService.class);

    public GmailService(GmailClient gmailClient, OAuth2AuthorizedClientService authorizedClientService) {
        this.gmailClient = gmailClient;
        this.authorizedClientService = authorizedClientService;
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient("google", authentication.getName());
        String accessToken = client.getAccessToken().getTokenValue();
        return gmailClient.createGmailService(accessToken);
    }

    public List<Message> listMessages(String userId) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages().list(userId).execute().getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    public List<Message> listLatestMessages(String userId, long maxResults) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setMaxResults(maxResults)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    public List<Message> listMessagesFromSender(String userId, String senderEmail) throws IOException {
        try {
            var gmail = getGmailService();
            return gmail.users().messages()
                    .list(userId)
                    .setQ("from:" + senderEmail)
                    .execute()
                    .getMessages();
        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

    public void deleteMessagesFromSender(String userId, String senderEmail) throws IOException {
        try {
            var gmail = getGmailService();

            // 1. Find all messages from the given sender
            var messages = gmail.users().messages()
                    .list(userId)
                    .setQ("from:" + senderEmail)
                    .setMaxResults(500L) // Optional: limit for batch operations
                    .execute()
                    .getMessages();

            if (messages == null || messages.isEmpty()) {
                logger.info("Found 0 matching messages");
                return; // No messages to delete
            }
            logger.info(String.format("Found %d matching messages", messages.size()));

            // 2. For each message, first move it to Trash, then permanently delete it
            logger.info("Moving messages to trash");
            for (var message : messages) {
                // Move the message to Trash
                gmail.users().messages().trash(userId, message.getId()).execute();
            }

            logger.info("Deleting messages");
            for (var message : messages) {
                // Now permanently delete it
                gmail.users().messages().delete(userId, message.getId()).execute();
            }

        } catch (GeneralSecurityException e) {
            throw new IOException("Security exception creating Gmail service", e);
        }
    }

}
