package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GmailQueryBuilder {
    
    private final GmailBuddyProperties properties;
    
    @Autowired
    public GmailQueryBuilder(GmailBuddyProperties properties) {
        this.properties = properties;
    }

    public String from(String senderEmail) {
        if (senderEmail == null || senderEmail.isBlank()) {
            return "";
        }
        return properties.gmailApi().queryOperators().from() + senderEmail + " ";
    }

    public String to(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return "";
        }
        return properties.gmailApi().queryOperators().to() + recipientEmail + " ";
    }

    public String subject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }
        return properties.gmailApi().queryOperators().subject() + subject + " ";
    }

    public String hasAttachment(Boolean hasAttachment) {
        if (hasAttachment == null || !hasAttachment) {
            return "";
        }
        return properties.gmailApi().queryOperators().hasAttachment();
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
