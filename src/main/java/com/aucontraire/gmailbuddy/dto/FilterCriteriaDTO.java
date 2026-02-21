package com.aucontraire.gmailbuddy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import com.aucontraire.gmailbuddy.validation.OptionalEmail;
import com.aucontraire.gmailbuddy.validation.ValidGmailQuery;

@Schema(description = "Filter criteria for searching Gmail messages")
public class FilterCriteriaDTO {

    @Schema(description = "Filter by sender email address", example = "sender@example.com")
    @OptionalEmail
    private String from;

    @Schema(description = "Filter by recipient email address", example = "recipient@example.com")
    @OptionalEmail
    private String to;

    @Schema(description = "Filter by subject containing this text", example = "Meeting invitation")
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;

    @Schema(description = "Filter messages with attachments", example = "true")
    private Boolean hasAttachment;

    @Schema(description = "Gmail search query syntax", example = "is:unread after:2024/01/01")
    @Size(max = 500, message = "Query must not exceed 500 characters")
    @ValidGmailQuery
    private String query;

    @Schema(description = "Exclude messages matching this query", example = "from:noreply@example.com")
    @Size(max = 500, message = "Negated query must not exceed 500 characters")
    @ValidGmailQuery
    private String negatedQuery;

    // Getters and setters
    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }
    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }
    public String getSubject() {
        return subject;
    }
    public void setSubject(String subject) {
        this.subject = subject;
    }
    public Boolean getHasAttachment() {
        return hasAttachment;
    }
    public void setHasAttachment(Boolean hasAttachment) {
        this.hasAttachment = hasAttachment;
    }
    public String getQuery() {
        return query;
    }
    public void setQuery(String query) {
        this.query = query;
    }
    public String getNegatedQuery() {
        return negatedQuery;
    }
    public void setNegatedQuery(String negatedQuery) {
        this.negatedQuery = negatedQuery;
    }
}
