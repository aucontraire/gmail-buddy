package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.service.GmailService;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gmail")
public class GmailController {

    private final GmailService gmailService;
    private OAuth2AuthorizedClientService authorizedClientService;
    private final Logger logger = LoggerFactory.getLogger(GmailController.class);


    @Autowired
    public GmailController(GmailService gmailService, OAuth2AuthorizedClientService authorizedClientService) {
        this.gmailService = gmailService;
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/messages")
    public String listMessages() {
        try {
            return gmailService.listMessages("me").toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch messages";
        }
    }

    @GetMapping("/messages/latest")
    public String listLatestFiftyMessages() {
        try {
            return gmailService.listLatestMessages("me", 50).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch the latest messages";
        }
    }

    @GetMapping("/messages/from/{email}")
    public String listMessagesFromSender(@PathVariable("email") String email) {
        try {
            List<Message> messages = gmailService.listMessagesFromSender("me", email);
            return messages != null ? messages.toString() : "No messages found";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch messages from sender: " + email;
        }
    }

    @DeleteMapping("/messages/from/{email}")
    public String deleteMessagesFromSender(@PathVariable("email") String email) {
        try {
            gmailService.deleteMessagesFromSender("me", email);
            return "Messages from " + email + " have been deleted.";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to delete messages from sender: " + email;
        }
    }

    @GetMapping("/debug/token")
    public String debugToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
            if (client != null) {
                String accessToken = client.getAccessToken().getTokenValue();
                System.out.println("Access Token: " + accessToken);
                return "Access Token: " + accessToken;
            }
        }
        return "No token found or user not authenticated.";
    }
}
