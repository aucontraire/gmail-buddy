package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.GmailBuddyApplication;
import com.aucontraire.gmailbuddy.controller.GmailController;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GmailController.class)
@ContextConfiguration(classes = GmailBuddyApplication.class)
class ControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @Test
    @WithMockUser
    void testValidFilterCriteria() throws Exception {
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setFrom("test@example.com");
        dto.setTo("recipient@example.com");
        dto.setSubject("Test Subject");
        dto.setQuery("in:inbox");

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testInvalidEmailValidation() throws Exception {
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setFrom("invalid-email");
        dto.setTo("also-invalid");

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.from").exists())
                .andExpect(jsonPath("$.details.to").exists());
    }

    @Test
    @WithMockUser
    void testSubjectTooLong() throws Exception {
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setSubject("a".repeat(256)); // Exceeds max length of 255

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.subject").exists());
    }

    @Test
    @WithMockUser
    void testInvalidQuerySyntax() throws Exception {
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setQuery("dangerous<script>alert('xss')</script>");

        mockMvc.perform(post("/api/v1/gmail/messages/filter")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.query").exists());
    }

    @Test
    @WithMockUser
    void testLabelValidationInFilterCriteriaWithLabels() throws Exception {
        FilterCriteriaWithLabelsDTO dto = new FilterCriteriaWithLabelsDTO();
        dto.setFrom("test@example.com");
        dto.setLabelsToAdd(List.of("", "valid-label", "a".repeat(51))); // Empty label and too long label
        dto.setLabelsToRemove(List.of("label1", "label2", "label3", "label4", "label5",
                                     "label6", "label7", "label8", "label9", "label10", "label11")); // Too many labels

        mockMvc.perform(post("/api/v1/gmail/messages/filter/modifyLabels")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @WithMockUser
    void testValidLabelModification() throws Exception {
        FilterCriteriaWithLabelsDTO dto = new FilterCriteriaWithLabelsDTO();
        dto.setFrom("test@example.com");
        dto.setLabelsToAdd(List.of("important", "work"));
        dto.setLabelsToRemove(List.of("spam"));

        mockMvc.perform(post("/api/v1/gmail/messages/filter/modifyLabels")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNoContent());
    }
}