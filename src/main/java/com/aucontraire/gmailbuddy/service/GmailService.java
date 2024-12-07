package com.aucontraire.gmailbuddy.service;

import com.google.api.services.gmail.model.Message;

import com.aucontraire.gmailbuddy.client.GmailClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GmailService {

    private final GmailClient gmailClient;

    public GmailService(GmailClient gmailClient) {
        this.gmailClient = gmailClient;
    }

    public List<Message> listMessages(String userId) throws IOException {
        return gmailClient.listMessages(userId);
    }

    // Define other methods like bulk deletion, labeling, etc.
}
