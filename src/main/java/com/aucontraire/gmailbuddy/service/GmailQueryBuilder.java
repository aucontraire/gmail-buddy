package com.aucontraire.gmailbuddy.service;

import org.springframework.stereotype.Component;

@Component
public class GmailQueryBuilder {

    public String from(String senderEmail) {
        if (senderEmail == null || senderEmail.isBlank()) {
            return "";
        }
        return "from:" + senderEmail + " ";
    }

    public String to(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return "";
        }
        return "to:" + recipientEmail + " ";
    }

    public String subject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }
        return "subject:" + subject + " ";
    }

    public String hasAttachment(Boolean hasAttachment) {
        if (hasAttachment == null || !hasAttachment) {
            return "";
        }
        return "has:attachment ";
    }

    public String query(String additionalQuery) {
        if (additionalQuery == null || additionalQuery.isBlank()) {
            return "";
        }
        return additionalQuery + " ";
    }

    public String negatedQuery(String negatedQuery) {
        if (negatedQuery == null || negatedQuery.isBlank()) {
            return "";
        }
        return negatedQuery + " ";
    }

    public String build(String... queryParts) {
        StringBuilder queryBuilder = new StringBuilder();
        for (String part : queryParts) {
            if (part != null && !part.isBlank()) {
                queryBuilder.append(part.trim()).append(" ");
            }
        }
        return queryBuilder.toString().trim();
    }
}
