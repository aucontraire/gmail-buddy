package com.aucontraire.gmailbuddy.client;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
public class GmailClient {

    public Gmail createGmailService(String accessToken) throws GeneralSecurityException, IOException {
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("gmail-buddy").build();
    }
}
