package com.aucontraire.gmailbuddy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.api.services.gmail.model.Message;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Lightweight message representation for list/search responses.
 * Contains essential message fields without full content.
 *
 * @since 1.0
 */
@Schema(description = "Summary of a Gmail message")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSummary {

    @Schema(description = "Unique message ID", example = "18d1a2b3c4d5e6f7")
    private String id;

    @Schema(description = "Thread ID this message belongs to", example = "18d1a2b3c4d5e6f7")
    private String threadId;

    @Schema(description = "Labels applied to this message", example = "[\"INBOX\", \"UNREAD\"]")
    private List<String> labelIds;

    @Schema(description = "Short snippet of the message content", example = "Hi there, I wanted to follow up on...")
    private String snippet;

    @Schema(description = "Internal date timestamp in milliseconds", example = "1705328400000")
    private Long internalDate;

    /**
     * Create a MessageSummary from a Gmail API Message object.
     *
     * @param message the Gmail API message
     * @return MessageSummary instance
     */
    public static MessageSummary from(Message message) {
        MessageSummary summary = new MessageSummary();
        summary.id = message.getId();
        summary.threadId = message.getThreadId();
        summary.labelIds = message.getLabelIds();
        summary.snippet = message.getSnippet();
        summary.internalDate = message.getInternalDate();
        return summary;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getThreadId() {
        return threadId;
    }

    public List<String> getLabelIds() {
        return labelIds;
    }

    public String getSnippet() {
        return snippet;
    }

    public Long getInternalDate() {
        return internalDate;
    }
}
