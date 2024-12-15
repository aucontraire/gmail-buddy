package com.aucontraire.gmailbuddy.service;

public class GmailQueryBuilder {
    private StringBuilder query;

    public GmailQueryBuilder() {
        query = new StringBuilder();
    }

    public GmailQueryBuilder from(String sender) {
        if (sender != null && !sender.isEmpty()) {
            append("from:" + sender);
        }
        return this;
    }

    public GmailQueryBuilder to(String recipient) {
        if (recipient != null && !recipient.isEmpty()) {
            append("to:" + recipient);
        }
        return this;
    }

    public GmailQueryBuilder subject(String subject) {
        if (subject != null && !subject.isEmpty()) {
            append("subject:" + subject);
        }
        return this;
    }

    public GmailQueryBuilder hasAttachment(boolean hasAttachment) {
        if (hasAttachment) {
            append("has:attachment");
        }
        return this;
    }

    public GmailQueryBuilder query(String query) {
        if (query != null && !query.isEmpty()) {
            append(query);
        }
        return this;
    }

    public GmailQueryBuilder negatedQuery(String negatedQuery) {
        if (negatedQuery != null && !negatedQuery.isEmpty()) {
            append(negatedQuery);
        }
        return this;
    }

    private void append(String clause) {
        if (query.length() > 0) {
            query.append(" AND ");
        }
        query.append(clause);
    }

    public String build() {
        return query.toString();
    }
}
