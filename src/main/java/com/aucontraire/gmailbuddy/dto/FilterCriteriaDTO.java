package com.aucontraire.gmailbuddy.dto;

import jakarta.validation.constraints.Size;
import com.aucontraire.gmailbuddy.validation.OptionalEmail;
import com.aucontraire.gmailbuddy.validation.ValidGmailQuery;

public class FilterCriteriaDTO {
    @OptionalEmail
    private String from;
    
    @OptionalEmail
    private String to;
    
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;
    
    private Boolean hasAttachment;
    
    @Size(max = 500, message = "Query must not exceed 500 characters")
    @ValidGmailQuery
    private String query;
    
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
