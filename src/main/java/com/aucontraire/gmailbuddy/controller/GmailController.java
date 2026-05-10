package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.config.ResponseHeaderFilter;
import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.aucontraire.gmailbuddy.dto.error.ErrorResponse;
import com.aucontraire.gmailbuddy.dto.response.BulkDeleteResponse;
import com.aucontraire.gmailbuddy.dto.response.DeleteOperationResult;
import com.aucontraire.gmailbuddy.dto.response.DraftDetailResponse;
import com.aucontraire.gmailbuddy.dto.response.DraftListResponse;
import com.aucontraire.gmailbuddy.dto.response.DraftResponse;
import com.aucontraire.gmailbuddy.dto.response.LabelModificationResponse;
import com.aucontraire.gmailbuddy.dto.response.SendMessageResponse;
import com.aucontraire.gmailbuddy.dto.response.MessageListResponse;
import com.aucontraire.gmailbuddy.dto.response.MessageSummary;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.aucontraire.gmailbuddy.util.LinkHeaderBuilder;
import com.google.api.services.gmail.model.Message;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/gmail")
@Tag(name = "Gmail", description = "Gmail message management operations")
@Validated
public class GmailController {

    private final GmailService gmailService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GmailBuddyProperties properties;
    private final ResponseMapper responseMapper;
    private final GmailMessageMapper gmailMessageMapper;
    private final Logger logger = LoggerFactory.getLogger(GmailController.class);

    @Autowired
    public GmailController(GmailService gmailService, OAuth2AuthorizedClientService authorizedClientService,
                          GmailBuddyProperties properties, ResponseMapper responseMapper,
                          GmailMessageMapper gmailMessageMapper) {
        this.gmailService = gmailService;
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
        this.responseMapper = responseMapper;
        this.gmailMessageMapper = gmailMessageMapper;
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

    /**
     * POST /drafts - Stage an email as a draft in the user's Gmail Drafts folder.
     *
     * <p>This is the default outreach path for AI-personalized content. The draft is
     * immediately visible in any Gmail client (web, mobile, desktop) for the user to
     * review, edit, send, or discard before delivery.</p>
     *
     * <p>The same {@link SendMessageDTO} payload is used for both direct-send and
     * draft-creation; the destination Gmail API call differs. Per the API contract,
     * this endpoint returns HTTP 201 Created with a {@code Location} header pointing
     * to the new draft resource.</p>
     */
    @Operation(
        summary = "Create a draft email",
        description = "Stages an email as a draft in the user's Gmail Drafts folder. " +
                      "The draft is immediately visible in Gmail for review, editing, or discard. " +
                      "This is the recommended path for AI-personalized outreach content. " +
                      "Returns HTTP 201 Created with a Location header pointing to the new draft."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Draft created successfully",
            content = @Content(schema = @Schema(implementation = DraftResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request - validation failure or header-injection detected",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Payload too large - body alone exceeds 10 MB OR total assembled MIME (body + attachments) exceeds 25 MB",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - Gmail rejected one or more recipient addresses (type: /problems/invalid-recipient)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - original message referenced by inReplyToMessageId not found in this account (type: /problems/original-message-not-found)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error - draft creation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/drafts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftResponse> createDraft(
            @Valid @RequestBody SendMessageDTO dto) {

        String userId = properties.gmailApi().defaultUserId();

        DraftCreationResult result = gmailService.createDraft(userId, dto);

        logger.info("Draft created: userId={}, draftId={}, recipientCount={}, to={}, cc={}, bcc={}, subject={}",
                userId, result.draftId(),
                dto.to().size() + dto.cc().size() + dto.bcc().size(),
                dto.to(), dto.cc(), dto.bcc(), dto.subject());

        DraftResponse body = DraftResponse.drafted(result.draftId(), result.messageId(), result.threadId());
        URI location = URI.create("/api/v1/gmail/drafts/" + result.draftId());

        return ResponseEntity.created(location).body(body);
    }

    /**
     * POST /drafts/{draftId}/send — Deliver a previously-staged draft.
     *
     * <p>Triggers immediate delivery of the draft identified by {@code draftId}.
     * This is a state transition on an existing draft resource, not creation,
     * so the response is HTTP 200 OK with no {@code Location} header (per
     * Decision 3 / contracts/api-endpoints.md Endpoint 3).</p>
     *
     * <p>Naturally idempotent: a successful send removes the draft from Gmail.
     * A retry returns HTTP 404 because the draft no longer exists (see Decision 6).</p>
     */
    @Operation(
        summary = "Send an existing draft",
        description = "Delivers a previously-created draft by its identifier. " +
                      "This is a state transition on an existing resource — the draft is consumed " +
                      "and the message appears in the Sent folder. " +
                      "Returns HTTP 200 OK (not 201) with no Location header. " +
                      "Naturally idempotent: a retry after successful send returns 404."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft sent successfully",
            content = @Content(schema = @Schema(implementation = SendMessageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Draft not found - already sent, discarded, or invalid draftId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Payload too large - Gmail rejected the assembled MIME because it exceeds Gmail's maximum allowed size (35 MB raw)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - Gmail rejected one or more recipient addresses",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Daily Gmail send limit reached - retry after 86400 seconds",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error - send failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/drafts/{draftId}/send",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendMessageResponse> sendDraft(
            @Parameter(description = "The Gmail-assigned draft identifier returned by POST /drafts", required = true)
            @PathVariable String draftId) {

        String userId = properties.gmailApi().defaultUserId();

        SentMessageResult result = gmailService.sendDraft(userId, draftId);

        logger.info("Draft sent for userId={}, draftId={}, messageId={}", userId, draftId, result.messageId());

        return ResponseEntity.ok(SendMessageResponse.sent(result.messageId(), result.threadId()));
    }

    /**
     * POST /messages — Send an email message immediately.
     *
     * <p>Sends the message constructed from the request body directly via the Gmail
     * API. Reserved for deterministic, pre-trusted templates. Per Decision 3 and
     * contracts/api-endpoints.md Endpoint 1, this returns HTTP 201 Created with a
     * {@code Location} header pointing to the body-fetch endpoint for the new
     * message resource.</p>
     *
     * <p><strong>At-least-once semantics</strong>: callers MUST NOT auto-retry
     * after a network timeout — duplicate sends may result (Decision 6, FR-023).</p>
     */
    @Operation(
        summary = "Send an email message directly",
        description = "Sends an email immediately via the Gmail API. " +
                      "Reserved for deterministic, pre-trusted templates — not the recommended path for " +
                      "AI-personalized content (use POST /drafts instead). " +
                      "Returns HTTP 201 Created with a Location header pointing to the message body endpoint. " +
                      "WARNING: callers must not auto-retry after a timeout — duplicate sends may result."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Message sent successfully — new message resource created",
            content = @Content(schema = @Schema(implementation = SendMessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request - validation failure or header-injection detected",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions or unverified send-as identity",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Payload too large - body alone exceeds 10 MB OR total assembled MIME (body + attachments) exceeds 25 MB",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - Gmail rejected one or more recipient addresses (type: /problems/invalid-recipient)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - original message referenced by inReplyToMessageId not found in this account (type: /problems/original-message-not-found)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Daily Gmail send limit reached - retry after 86400 seconds",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error - send failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/messages",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendMessageResponse> sendMessage(
            @Valid @RequestBody SendMessageDTO dto) {

        String userId = properties.gmailApi().defaultUserId();

        SentMessageResult result = gmailService.sendMessage(userId, dto);

        logger.info("Message sent: userId={}, messageId={}, recipientCount={}, to={}, cc={}, bcc={}, subject={}",
                userId, result.messageId(),
                dto.to().size() + dto.cc().size() + dto.bcc().size(),
                dto.to(), dto.cc(), dto.bcc(), dto.subject());

        URI location = URI.create("/api/v1/gmail/messages/" + result.messageId() + "/body");
        return ResponseEntity.created(location).body(SendMessageResponse.sent(result.messageId(), result.threadId()));
    }

    /**
     * GET /drafts — List pending drafts with pagination.
     *
     * <p>Calls {@code users.drafts.list} then fetches each draft with
     * {@code users.drafts.get(format=FULL)} to populate recipients, subject,
     * and snippet. Updates the {@code X-Gmail-Quota-Used} request attribute
     * post-execution with the actual cost: {@code 1 + N * 5}.</p>
     */
    @Operation(
        summary = "List pending drafts",
        description = "Returns a paginated list of draft summaries. Each item includes recipients, subject, snippet, " +
                      "thread ID, and attachment count. Internally calls users.drafts.get per item for enrichment."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved drafts",
            content = @Content(schema = @Schema(implementation = DraftListResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid query parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions (requires gmail.modify scope)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Gmail API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/drafts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftListResponse> listDrafts(
            @Parameter(description = "Pagination token from a prior response (max 255 chars)")
            @RequestParam(required = false) @Size(max = 255) String pageToken,
            @Parameter(description = "Maximum number of drafts to return (1–50, default 25)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {

        String userId = properties.gmailApi().defaultUserId();
        DraftListResult result = gmailService.listDrafts(userId, pageToken, limit);

        // Update actual quota: 1 (list call) + N * 5 (per-item get calls)
        int actualQuota = 1 + result.drafts().size() * 5;
        request.setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, actualQuota);

        DraftListResponse response = gmailMessageMapper.toDraftListResponse(result);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /drafts/{draftId} — Get full content of a single draft.
     *
     * <p>Calls {@code users.drafts.get(format=FULL)} and returns the full
     * {@link DraftDetailResponse} including recipients, subject, body,
     * threading fields, and attachment metadata.</p>
     */
    @Operation(
        summary = "Get draft detail",
        description = "Returns the full contents of a single draft including recipients, subject, body, " +
                      "threading fields, and attachment metadata. No binary content is returned."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft retrieved successfully",
            content = @Content(schema = @Schema(implementation = DraftDetailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid draft ID format",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions (requires gmail.modify scope)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Draft not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Gmail API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/drafts/{draftId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftDetailResponse> getDraft(
            @Parameter(description = "The Gmail-assigned draft identifier", required = true)
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String draftId) {

        String userId = properties.gmailApi().defaultUserId();
        DraftDetailResult result = gmailService.getDraft(userId, draftId);
        DraftDetailResponse response = gmailMessageMapper.toDraftDetailResponse(result);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /drafts/{draftId} — Permanently delete a draft.
     *
     * <p>Calls {@code users.drafts.delete}. Hard delete — the draft does not go
     * to Trash. Returns 204 No Content on success.</p>
     */
    @Operation(
        summary = "Delete a draft",
        description = "Permanently deletes a draft. The draft is hard-deleted (not moved to Trash). " +
                      "Returns 204 No Content on success. If the draft does not exist, returns 404."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Draft deleted successfully (empty response body)"),
        @ApiResponse(responseCode = "400", description = "Invalid draft ID format",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions (requires gmail.modify scope)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Draft not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Gmail API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping(value = "/drafts/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteDraft(
            @Parameter(description = "The Gmail-assigned draft identifier", required = true)
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String draftId) {

        String userId = properties.gmailApi().defaultUserId();
        gmailService.deleteDraft(userId, draftId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /drafts/{draftId} — Replace the content of an existing draft.
     *
     * <p>Full replacement via {@code users.drafts.update}. All fields (recipients,
     * subject, body, threading, attachments) are replaced by the request body.
     * Omitted optional fields (cc, bcc, attachments, threading) are cleared.
     * Returns the updated {@link DraftDetailResponse}.</p>
     */
    @Operation(
        summary = "Update a draft",
        description = "Replaces the content of an existing draft (full replacement — not a partial update). " +
                      "Omitted optional fields (cc, bcc, attachments, inReplyToMessageId) are cleared. " +
                      "Returns the updated draft detail. When inReplyToMessageId is present, a threading " +
                      "lookup is performed first (total ~20 quota units instead of 15)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Draft updated successfully",
            content = @Content(schema = @Schema(implementation = DraftDetailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request - validation failure or header-injection detected",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient Gmail permissions (requires gmail.modify scope)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Draft not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Payload too large - assembled MIME exceeds 25 MB",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unprocessable entity - invalid recipient or inReplyToMessageId not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Gmail API error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Gmail API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/drafts/{draftId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftDetailResponse> updateDraft(
            @Parameter(description = "The Gmail-assigned draft identifier", required = true)
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String draftId,
            @Valid @RequestBody SendMessageDTO dto,
            HttpServletRequest request) {

        String userId = properties.gmailApi().defaultUserId();
        DraftDetailResult result = gmailService.updateDraft(userId, draftId, dto);

        // If threading lookup was performed, quota is 15 (update) + 5 (lookup) = 20
        if (dto.inReplyToMessageId() != null) {
            request.setAttribute(ResponseHeaderFilter.ATTR_GMAIL_QUOTA_USED, 20);
        }

        DraftDetailResponse response = gmailMessageMapper.toDraftDetailResponse(result);
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
