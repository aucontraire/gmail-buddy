package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
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
}
