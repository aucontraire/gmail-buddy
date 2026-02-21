package com.aucontraire.gmailbuddy.validation;

import com.aucontraire.gmailbuddy.controller.GmailController;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.mapper.ResponseMapper;
import com.aucontraire.gmailbuddy.ratelimit.GmailQuotaEstimator;
import com.aucontraire.gmailbuddy.ratelimit.RateLimitService;
import com.aucontraire.gmailbuddy.security.TokenReferenceService;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.service.GoogleTokenValidator;
import com.aucontraire.gmailbuddy.service.MessageListResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GmailController.class)
@Import(TestGmailBuddyPropertiesConfiguration.class)
class ControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GmailService gmailService;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private GoogleTokenValidator tokenValidator;

    @MockitoBean
    private TokenReferenceService tokenReferenceService;

    @MockitoBean
    private ResponseMapper responseMapper;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private GmailQuotaEstimator gmailQuotaEstimator;

    @Test
    @WithMockUser
    void testValidFilterCriteria() throws Exception {
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setFrom("test@example.com");
        dto.setTo("recipient@example.com");
        dto.setSubject("Test Subject");
        dto.setQuery("in:inbox");

        // Mock the paginated service method
        MessageListResult mockResult = new MessageListResult(Collections.emptyList(), null, 0);
        when(gmailService.listMessagesByFilterCriteriaWithPagination(eq("me"), any(FilterCriteriaDTO.class), any(), anyInt()))
                .thenReturn(mockResult);

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
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.extensions['field:from']").exists())
                .andExpect(jsonPath("$.extensions['field:to']").exists());
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
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.extensions['field:subject']").exists());
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
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.extensions['field:query']").exists());
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
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.extensions").exists());
    }

    @Test
    @WithMockUser
    void testValidLabelModification() throws Exception {
        FilterCriteriaWithLabelsDTO dto = new FilterCriteriaWithLabelsDTO();
        dto.setFrom("test@example.com");
        dto.setLabelsToAdd(List.of("important", "work"));
        dto.setLabelsToRemove(List.of("spam"));

        // Mock the service method to return a BulkOperationResult
        BulkOperationResult mockResult = new BulkOperationResult(BulkOperationResult.OPERATION_TYPE_BATCH_MODIFY);
        mockResult.markCompleted();
        when(gmailService.modifyMessagesLabelsByFilterCriteria(eq("me"), any(FilterCriteriaWithLabelsDTO.class)))
                .thenReturn(mockResult);

        // The endpoint now returns 200 OK with a LabelModificationResponse instead of 204 No Content
        mockMvc.perform(post("/api/v1/gmail/messages/filter/modifyLabels")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}