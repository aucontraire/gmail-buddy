package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.LabelModificationRequest;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<Message>> listMessages() {
        try {
            List<Message> messages = gmailService.listMessages("me");
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/messages/latest")
    public ResponseEntity<List<Message>> listLatestMessages() {
        try {
            List<Message> messages = gmailService.listLatestMessages("me", 50);
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch latest messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/messages/from/{email}")
    public ResponseEntity<List<Message>> listMessagesFromSender(
            @PathVariable("email") String email,
            @RequestBody FilterCriteria filterCriteria) {
        try {
            List<Message> messages = gmailService.listMessagesFromSender("me", email, filterCriteria);
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch messages from sender: " + email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/messages/from/{email}")
    public ResponseEntity<Void> deleteMessagesFromSender(
            @PathVariable("email") String email,
            @RequestBody FilterCriteria filterCriteria) {
        try {
            gmailService.deleteMessagesFromSender("me", email, filterCriteria);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (GmailServiceException e) {
            logger.error("Failed to delete messages from sender: " + email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/messages/from/{email}/modifyLabels")
    public ResponseEntity<Void> modifyMessagesLabels(
            @PathVariable String email,
            @RequestBody LabelModificationRequest request
    ) {
        try {
            gmailService.modifyMessagesLabels("me", email, request.getLabelsToAdd(), request.getLabelsToRemove());
            return ResponseEntity.noContent().build();
        } catch (GmailServiceException e) {
            logger.error("Failed to modify labels for email: " + email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/messages/{messageId}/body")
    public ResponseEntity<String> getMessageBody(@PathVariable String messageId) {
        try {
            String messageBody = gmailService.getMessageBody("me", messageId);
            return ResponseEntity.ok(messageBody);
        } catch (MessageNotFoundException e) {
            logger.error("Message not found for messageId: " + messageId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (GmailServiceException e) {
            logger.error("Failed to get message body for messageId: " + messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
