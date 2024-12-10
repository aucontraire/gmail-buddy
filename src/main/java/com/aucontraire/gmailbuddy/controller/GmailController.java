package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.service.GmailService;
import com.google.api.services.gmail.model.Message;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @GetMapping("/messages")
    public String listMessages() {
        try {
            return gmailService.listMessages("me").toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch messages";
        }
    }

    @GetMapping("/messages/latest")
    public String listLatestFiftyMessages() {
        try {
            return gmailService.listLatestMessages("me", 50).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch the latest messages";
        }
    }

    @GetMapping("/messages/from/{email}")
    public String listMessagesFromSender(@PathVariable("email") String email) {
        try {
            List<Message> messages = gmailService.listMessagesFromSender("me", email);
            return messages != null ? messages.toString() : "No messages found";
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to fetch messages from sender: " + email;
        }
    }
}
