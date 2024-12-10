package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.service.GmailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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
}
