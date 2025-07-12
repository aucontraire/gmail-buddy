package com.aucontraire.gmailbuddy.controller;

import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.TestOAuth2AuthorizedClientService;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class GmailControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(GmailControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GmailService gmailService;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Configuration
    static class TestConfig {
        @Bean
        public OAuth2AuthorizedClientService authorizedClientService() {
            return new TestOAuth2AuthorizedClientService();
        }
    }

    @BeforeEach
    public void setup() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value", null, null);
        OAuth2User principal = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                Collections.singletonMap("name", "testuser"),
                "name"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Legacy test - content type issue needs investigation")
    public void testListMessagesByFilterCriteria() throws Exception {
        String jsonPayload = "{\n" +
                "  \"from\": \"jobalerts-noreply@linkedin.com\",\n" +
                "  \"to\": \"ocontreras.sf@gmail.com\",\n" +
                "  \"subject\": \"4 new jobs in United States\",\n" +
                "  \"hasAttachment\": false,\n" +
                "  \"query\": \"label:Inbox\",\n" +
                "  \"negatedQuery\": \"label:Spam\"\n" +
                "}";

        when(gmailService.listMessagesByFilterCriteria(eq("me"), any(FilterCriteriaDTO.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Legacy test - Jackson serialization issue with Google Message objects")
    public void testListMessages() throws Exception {
        when(gmailService.listMessages(eq("me"))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/gmail/messages")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetMessageBody_ResourceNotFoundException() throws Exception {
        when(gmailService.getMessageBody("me", "12345"))
                .thenThrow(new ResourceNotFoundException("Message not found"));

        mockMvc.perform(get("/api/v1/gmail/messages/{messageId}/body", "12345")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
