package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.LabelModificationRequest;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listMessages() {
        try {
            List<Message> messages = gmailService.listMessages("me");
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/messages/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listLatestMessages() {
        try {
            List<Message> messages = gmailService.listLatestMessages("me", 50);
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch latest messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listMessagesByFilterCriteria(
            @RequestBody FilterCriteriaDTO filterCriteriaDTO) {
        try {
            // Map the DTO to the actual FilterCriteria required by your service
            // For example, you can create a helper method in your service for mapping
            List<Message> messages = gmailService.listMessagesByFilterCriteria("me", filterCriteriaDTO);
            return ResponseEntity.ok(messages);
        } catch (GmailServiceException e) {
            logger.error("Failed to fetch messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping(value = "/messages/{messageId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        try {
            gmailService.deleteMessage("me", messageId);
            return ResponseEntity.noContent().build();
        } catch (GmailServiceException e) {
            logger.error("Failed to delete message with id: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteMessagesByFilterCriteria(
            @RequestBody FilterCriteriaDTO filterCriteriaDTO) {
        try {
            gmailService.deleteMessagesByFilterCriteria("me", filterCriteriaDTO);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (GmailServiceException e) {
            logger.error("Failed to delete messages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/messages/filter/modifyLabels",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> modifyMessagesLabelsByFilter(
            @RequestBody FilterCriteriaWithLabelsDTO dto) {
        try {
            gmailService.modifyMessagesLabelsByFilterCriteria("me", dto);
            return ResponseEntity.noContent().build();
        } catch (GmailServiceException e) {
            logger.error("Failed to modify labels with filter criteria", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/messages/{messageId}/body", produces = MediaType.TEXT_HTML_VALUE)
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

    @PutMapping(value = "/messages/{messageId}/read",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> markMessageAsRead(@PathVariable String messageId) {
        try {
            gmailService.markMessageAsRead("me", messageId);
            return ResponseEntity.noContent().build();
        } catch (GmailServiceException e) {
            logger.error("Failed to mark message as read for messageId: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/debug/token", produces = MediaType.APPLICATION_JSON_VALUE)
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
