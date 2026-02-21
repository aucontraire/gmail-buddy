package com.aucontraire.gmailbuddy.integration;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests to verify that response headers are properly added to all HTTP responses.
 * Tests X-Request-ID and X-Response-Time headers across different endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResponseHeaderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private GmailBuddyProperties properties;

    @Test
    @WithMockUser
    void dashboard_shouldIncludeResponseHeaders() throws Exception {
        // Act & Assert
        MvcResult result = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().exists("X-Response-Time"))
                .andReturn();

        // Verify header formats
        String requestId = result.getResponse().getHeader("X-Request-ID");
        String responseTime = result.getResponse().getHeader("X-Response-Time");

        assertThat(requestId).isNotNull();
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        assertThat(responseTime).isNotNull();
        assertThat(Long.parseLong(responseTime)).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @WithMockUser
    void apiEndpoint_shouldIncludeResponseHeaders() throws Exception {
        // Note: This test may fail with 401/403 if OAuth2 setup is incomplete
        // but we're primarily testing header presence on the response
        MvcResult result = mockMvc.perform(get("/api/v1/gmail/messages/latest"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().exists("X-Response-Time"))
                .andReturn();

        // Verify header formats
        String requestId = result.getResponse().getHeader("X-Request-ID");
        String responseTime = result.getResponse().getHeader("X-Response-Time");

        assertThat(requestId).isNotNull();
        assertThat(responseTime).isNotNull();
    }

    @Test
    @WithMockUser
    void nonExistentEndpoint_shouldStillIncludeResponseHeaders() throws Exception {
        // Act & Assert - even 404 responses should have headers
        MvcResult result = mockMvc.perform(get("/api/v1/gmail/nonexistent"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().exists("X-Response-Time"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-ID");
        String responseTime = result.getResponse().getHeader("X-Response-Time");

        assertThat(requestId).isNotNull();
        assertThat(responseTime).isNotNull();
    }

    @Test
    @WithMockUser
    void requestWithProvidedRequestId_shouldUseProvidedId() throws Exception {
        // Arrange
        String providedRequestId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        MvcResult result = mockMvc.perform(get("/dashboard")
                        .header("X-Request-ID", providedRequestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", providedRequestId))
                .andExpect(header().exists("X-Response-Time"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(requestId).isEqualTo(providedRequestId);
    }

    @Test
    @WithMockUser
    void responseTime_shouldBeReasonable() throws Exception {
        // Act
        MvcResult result = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Response-Time"))
                .andReturn();

        // Assert - response time should be less than 5 seconds for simple dashboard
        String responseTime = result.getResponse().getHeader("X-Response-Time");
        Long responseTimeMs = Long.parseLong(responseTime);

        assertThat(responseTimeMs).isGreaterThanOrEqualTo(0L);
        assertThat(responseTimeMs).isLessThan(5000L); // Less than 5 seconds
    }

    @Test
    @WithMockUser
    void multipleRequests_shouldHaveDifferentRequestIds() throws Exception {
        // Act - make multiple requests
        MvcResult result1 = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - request IDs should be different
        String requestId1 = result1.getResponse().getHeader("X-Request-ID");
        String requestId2 = result2.getResponse().getHeader("X-Request-ID");

        assertThat(requestId1).isNotNull();
        assertThat(requestId2).isNotNull();
        assertThat(requestId1).isNotEqualTo(requestId2);
    }

    @Test
    @WithMockUser
    void requestIdFormat_shouldBeValidUUID() throws Exception {
        // Act
        MvcResult result = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String requestId = result.getResponse().getHeader("X-Request-ID");
        assertThat(requestId)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
