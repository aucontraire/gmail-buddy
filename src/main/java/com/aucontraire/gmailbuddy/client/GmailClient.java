package com.aucontraire.gmailbuddy.client;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GmailClient {

    private final Gmail gmailService;

    public GmailClient() throws GeneralSecurityException, IOException {
        // Load client secrets.
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                jsonFactory, new InputStreamReader(new FileInputStream("src/main/resources/credentials.json"))
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                jsonFactory,
                clientSecrets,
                List.of("https://www.googleapis.com/auth/gmail.readonly", "https://www.googleapis.com/auth/gmail.modify")
        ).setAccessType("offline").build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8020).setCallbackPath("/oauth2/callback").build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        this.gmailService = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                jsonFactory,
                credential
        ).setApplicationName("gmail-buddy").build();
    }

    public List<Message> listMessages(String userId) throws IOException {
        return gmailService.users().messages().list(userId).execute().getMessages();
    }

    // Additional methods to delete or label messages can be added here.
}
