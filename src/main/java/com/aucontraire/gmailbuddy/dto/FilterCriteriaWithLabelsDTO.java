package com.aucontraire.gmailbuddy.dto;

import java.util.List;

public class FilterCriteriaWithLabelsDTO {
    private String from;
    private String to;
    private String subject;
    private Boolean hasAttachment;
    private String query;
    private String negatedQuery;
    private List<String> labelsToAdd;
    private List<String> labelsToRemove;

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
    public List<String> getLabelsToAdd() {
        return labelsToAdd;
    }
    public void setLabelsToAdd(List<String> labelsToAdd) {
        this.labelsToAdd = labelsToAdd;
    }
    public List<String> getLabelsToRemove() {
        return labelsToRemove;
    }
    public void setLabelsToRemove(List<String> labelsToRemove) {
        this.labelsToRemove = labelsToRemove;
    }
}
