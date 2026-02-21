package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.aucontraire.gmailbuddy.dto.error.ErrorResponse;
import com.aucontraire.gmailbuddy.dto.response.BulkDeleteResponse;
import com.aucontraire.gmailbuddy.dto.response.DeleteOperationResult;
import com.aucontraire.gmailbuddy.dto.response.LabelModificationResponse;
import com.aucontraire.gmailbuddy.dto.response.MessageListResponse;
import com.aucontraire.gmailbuddy.dto.response.MessageSummary;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.aucontraire.gmailbuddy.util.LinkHeaderBuilder;
import com.google.api.services.gmail.model.Message;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/gmail")
@Tag(name = "Gmail", description = "Gmail message management operations")
public class GmailController {

    private final GmailService gmailService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GmailBuddyProperties properties;
    private final ResponseMapper responseMapper;
    private final Logger logger = LoggerFactory.getLogger(GmailController.class);

    @Autowired
    public GmailController(GmailService gmailService, OAuth2AuthorizedClientService authorizedClientService,
                          GmailBuddyProperties properties, ResponseMapper responseMapper) {
        this.gmailService = gmailService;
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
        this.responseMapper = responseMapper;
    }

    /**
     * GET /messages - List messages with pagination support.
     * Returns a paginated list of message summaries with Link headers for navigation.
     */
    @Operation(
        summary = "List messages",
        description = "Returns a paginated list of message summaries with Link headers for navigation (RFC 5988)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved messages",
            content = @Content(schema = @Schema(implementation = MessageListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageListResponse> listMessages(
            @Parameter(description = "Page token for pagination (from previous response)")
            @RequestParam(required = false) String pageToken,
            @Parameter(description = "Maximum number of messages to return (default: 50)")
            @RequestParam(defaultValue = "50") int limit) {

        long startTime = System.currentTimeMillis();
        String userId = properties.gmailApi().defaultUserId();

        MessageListResult result = gmailService.listMessagesWithPagination(userId, pageToken, limit);

        List<MessageSummary> summaries = convertToSummaries(result.getMessages());
        long duration = System.currentTimeMillis() - startTime;

        MessageListResponse response = MessageListResponse.builder()
            .messages(summaries)
            .totalCount(result.getResultSizeEstimate())
            .hasMore(result.hasMore())
            .nextPageToken(result.getNextPageToken())
            .metadata(ResponseMetadata.builder().durationMs(duration).build())
            .build();

        // Build Link header for pagination (RFC 5988)
        String linkHeader = new LinkHeaderBuilder()
            .addFirst()
            .addNext(result.getNextPageToken())
            .build();

        HttpHeaders headers = new HttpHeaders();
        if (linkHeader != null) {
            headers.add(HttpHeaders.LINK, linkHeader);
        }

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * GET /messages/latest - List the latest N messages.
     * Returns message summaries for the most recent messages.
     */
    @Operation(
        summary = "List latest messages",
        description = "Returns the most recent messages (default: 50) with pagination support"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved latest messages",
            content = @Content(schema = @Schema(implementation = MessageListResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/messages/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageListResponse> listLatestMessages(
            @Parameter(description = "Page token for pagination")
            @RequestParam(required = false) String pageToken) {

        long startTime = System.currentTimeMillis();
        String userId = properties.gmailApi().defaultUserId();
        int limit = properties.gmailApi().defaultLatestMessagesLimit();

        MessageListResult result = gmailService.listLatestMessagesWithPagination(userId, pageToken, limit);

        List<MessageSummary> summaries = convertToSummaries(result.getMessages());
        long duration = System.currentTimeMillis() - startTime;

        MessageListResponse response = MessageListResponse.builder()
            .messages(summaries)
            .totalCount(summaries.size())
            .hasMore(result.hasMore())
            .nextPageToken(result.getNextPageToken())
            .metadata(ResponseMetadata.builder().durationMs(duration).build())
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /messages/filter - Search messages by filter criteria.
     * Returns paginated message summaries matching the provided criteria.
     */
    @Operation(
        summary = "Filter messages",
        description = "Search messages by various criteria including sender, subject, date range, and labels"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully filtered messages",
            content = @Content(schema = @Schema(implementation = MessageListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter criteria",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageListResponse> listMessagesByFilterCriteria(
            @Valid @RequestBody FilterCriteriaDTO filterCriteriaDTO,
            @Parameter(description = "Page token for pagination")
            @RequestParam(required = false) String pageToken,
            @Parameter(description = "Maximum number of messages to return")
            @RequestParam(defaultValue = "50") int limit) {

        long startTime = System.currentTimeMillis();
        String userId = properties.gmailApi().defaultUserId();

        MessageListResult result = gmailService.listMessagesByFilterCriteriaWithPagination(userId, filterCriteriaDTO, pageToken, limit);

        List<MessageSummary> summaries = convertToSummaries(result.getMessages());
        long duration = System.currentTimeMillis() - startTime;

        MessageListResponse response = MessageListResponse.builder()
            .messages(summaries)
            .totalCount(result.getResultSizeEstimate())
            .hasMore(result.hasMore())
            .nextPageToken(result.getNextPageToken())
            .metadata(ResponseMetadata.builder().durationMs(duration).build())
            .build();

        // Build Link header for pagination (RFC 5988)
        String linkHeader = new LinkHeaderBuilder()
            .addFirst()
            .addNext(result.getNextPageToken())
            .build();

        HttpHeaders headers = new HttpHeaders();
        if (linkHeader != null) {
            headers.add(HttpHeaders.LINK, linkHeader);
        }

        return ResponseEntity.ok().headers(headers).body(response);
    }

    /**
     * DELETE /messages/{messageId} - Delete a single message.
     * Returns detailed result information including status and timing metadata.
     */
    @Operation(
        summary = "Delete a message",
        description = "Permanently deletes a single message by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message deleted successfully",
            content = @Content(schema = @Schema(implementation = DeleteOperationResult.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Message not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping(value = "/messages/{messageId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeleteOperationResult> deleteMessage(
            @Parameter(description = "The ID of the message to delete", required = true)
            @PathVariable String messageId) {
        long startTime = System.currentTimeMillis();
        String userId = properties.gmailApi().defaultUserId();

        DeleteResult result = gmailService.deleteMessage(userId, messageId);
        long duration = System.currentTimeMillis() - startTime;

        logger.info("Delete message result: {}", result);

        DeleteOperationResult response;
        if (result.isSuccess()) {
            response = DeleteOperationResult.success(messageId)
                .withMetadata(ResponseMetadata.builder().durationMs(duration).build());
        } else {
            response = DeleteOperationResult.failure(messageId, result.getMessage())
                .withMetadata(ResponseMetadata.builder().durationMs(duration).build());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /messages/filter - Bulk delete messages matching filter criteria.
     * Returns detailed bulk operation result with success/failure counts and affected message IDs.
     */
    @Operation(
        summary = "Bulk delete messages",
        description = "Permanently deletes all messages matching the provided filter criteria"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bulk delete operation completed",
            content = @Content(schema = @Schema(implementation = BulkDeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter criteria",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping(value = "/messages/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BulkDeleteResponse> deleteMessagesByFilterCriteria(
            @Valid @RequestBody FilterCriteriaDTO filterCriteriaDTO) {

        String userId = properties.gmailApi().defaultUserId();
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        logger.info("Bulk delete result: {}", result);

        BulkDeleteResponse response = responseMapper.toBulkDeleteResponse(result);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /messages/filter/modifyLabels - Modify labels on messages matching filter criteria.
     * Returns detailed label modification result with status and affected message information.
     */
    @Operation(
        summary = "Modify message labels",
        description = "Add or remove labels from messages matching the provided filter criteria"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Labels modified successfully",
            content = @Content(schema = @Schema(implementation = LabelModificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request - missing labels or invalid criteria",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/messages/filter/modifyLabels",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LabelModificationResponse> modifyMessagesLabelsByFilter(
            @Valid @RequestBody FilterCriteriaWithLabelsDTO dto) {

        String userId = properties.gmailApi().defaultUserId();
        BulkOperationResult result = gmailService.modifyMessagesLabelsByFilterCriteria(userId, dto);

        List<String> labelsAdded = dto.getLabelsToAdd() != null ? dto.getLabelsToAdd() : Collections.emptyList();
        List<String> labelsRemoved = dto.getLabelsToRemove() != null ? dto.getLabelsToRemove() : Collections.emptyList();

        LabelModificationResponse response = responseMapper.toLabelModificationResponse(result, labelsAdded, labelsRemoved);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /messages/{messageId}/body - Get the body content of a specific message.
     * Returns the message body as HTML or plain text with timing metadata in headers.
     */
    @Operation(
        summary = "Get message body",
        description = "Retrieves the body content of a message as HTML or plain text"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message body retrieved successfully",
            content = @Content(mediaType = "text/html", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Message not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/messages/{messageId}/body", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getMessageBody(
            @Parameter(description = "The ID of the message", required = true)
            @PathVariable String messageId) {
        long startTime = System.currentTimeMillis();
        String userId = properties.gmailApi().defaultUserId();

        String messageBody = gmailService.getMessageBody(userId, messageId);
        long duration = System.currentTimeMillis() - startTime;

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Duration-Ms", String.valueOf(duration));

        return ResponseEntity.ok().headers(headers).body(messageBody);
    }

    /**
     * PUT /messages/{messageId}/read - Mark a message as read.
     * Returns label modification response with status and timing metadata.
     */
    @Operation(
        summary = "Mark message as read",
        description = "Marks a message as read by removing the UNREAD label"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message marked as read",
            content = @Content(schema = @Schema(implementation = LabelModificationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Message not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/messages/{messageId}/read",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LabelModificationResponse> markMessageAsRead(
            @Parameter(description = "The ID of the message to mark as read", required = true)
            @PathVariable String messageId) {
        String userId = properties.gmailApi().defaultUserId();
        BulkOperationResult result = gmailService.markMessageAsRead(userId, messageId);

        // For mark as read, we're removing the UNREAD label
        String unreadLabel = properties.gmailApi().messageProcessing().labels().unread();
        List<String> labelsRemoved = List.of(unreadLabel);

        LabelModificationResponse response = responseMapper.toLabelModificationResponse(
            result,
            Collections.emptyList(),
            labelsRemoved
        );

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Debug OAuth token",
        description = "Returns the current OAuth2 access token for debugging purposes. Use this to get a Bearer token for Postman."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "string"))),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
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

    /**
     * Converts a list of Gmail API Message objects to MessageSummary DTOs.
     * Handles null input gracefully by returning an empty list.
     *
     * @param messages list of Gmail API messages
     * @return list of MessageSummary DTOs
     */
    private List<MessageSummary> convertToSummaries(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
            .map(MessageSummary::from)
            .collect(Collectors.toList());
    }
}
