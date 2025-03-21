package com.aucontraire.gmailbuddy.dto;

public class FilterCriteriaDTO {
    private String from;
    private String to;
    private String subject;
    private Boolean hasAttachment;
    private String query;
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
