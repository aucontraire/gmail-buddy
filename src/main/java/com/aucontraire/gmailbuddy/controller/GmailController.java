package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
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
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gmail")
public class GmailController {

    private final GmailService gmailService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GmailBuddyProperties properties;
    private final Logger logger = LoggerFactory.getLogger(GmailController.class);

    @Autowired
    public GmailController(GmailService gmailService, OAuth2AuthorizedClientService authorizedClientService, 
                          GmailBuddyProperties properties) {
        this.gmailService = gmailService;
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
    }

    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listMessages() {
        String userId = properties.gmailApi().defaultUserId();
        List<Message> messages = gmailService.listMessages(userId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping(value = "/messages/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listLatestMessages() {
        String userId = properties.gmailApi().defaultUserId();
        int limit = properties.gmailApi().defaultLatestMessagesLimit();
        List<Message> messages = gmailService.listLatestMessages(userId, limit);
        return ResponseEntity.ok(messages);
    }

    @PostMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> listMessagesByFilterCriteria(
            @Valid @RequestBody FilterCriteriaDTO filterCriteriaDTO) {
        String userId = properties.gmailApi().defaultUserId();
        List<Message> messages = gmailService.listMessagesByFilterCriteria(userId, filterCriteriaDTO);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping(value = "/messages/{messageId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        String userId = properties.gmailApi().defaultUserId();
        gmailService.deleteMessage(userId, messageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteMessagesByFilterCriteria(
            @Valid @RequestBody FilterCriteriaDTO filterCriteriaDTO) {
        String userId = properties.gmailApi().defaultUserId();
        gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    @PostMapping(value = "/messages/filter/modifyLabels",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> modifyMessagesLabelsByFilter(
            @Valid @RequestBody FilterCriteriaWithLabelsDTO dto) {
        String userId = properties.gmailApi().defaultUserId();
        gmailService.modifyMessagesLabelsByFilterCriteria(userId, dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/messages/{messageId}/body", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getMessageBody(@PathVariable String messageId) {
        String userId = properties.gmailApi().defaultUserId();
        String messageBody = gmailService.getMessageBody(userId, messageId);
        return ResponseEntity.ok(messageBody);
    }

    @PutMapping(value = "/messages/{messageId}/read",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> markMessageAsRead(@PathVariable String messageId) {
        String userId = properties.gmailApi().defaultUserId();
        gmailService.markMessageAsRead(userId, messageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/debug/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public String debugToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            String registrationId = properties.oauth2().clientRegistrationId();
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(registrationId, oauthToken.getName());
            if (client != null) {
                String accessToken = client.getAccessToken().getTokenValue();
                String tokenPrefix = properties.oauth2().token().prefix();
                System.out.println("Access Token: " + tokenPrefix + accessToken);
                return "Access Token: " + tokenPrefix + accessToken;
            }
        }
        return "No token found or user not authenticated.";
    }
}
