package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.service.GmailService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/gmail")
public class GmailController {

    private final GmailService gmailService;

    private OAuth2AuthorizedClientService authorizedClientService;


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
}
