package com.aucontraire.gmailbuddy.dto.response;

import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageSummary DTO.
 * Tests the factory method and all getters to ensure proper mapping from Gmail API Message objects.
 */
@DisplayName("MessageSummary Tests")
class MessageSummaryTest {

    @Test
    @DisplayName("from() creates MessageSummary from valid Gmail API Message")
    void from_WithValidMessage_CreatesMessageSummary() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-123");
        gmailMessage.setThreadId("thread-456");
        gmailMessage.setLabelIds(Arrays.asList("INBOX", "UNREAD"));
        gmailMessage.setSnippet("This is a test message snippet");
        gmailMessage.setInternalDate(1234567890000L);

        // When
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo("msg-123");
        assertThat(summary.getThreadId()).isEqualTo("thread-456");
        assertThat(summary.getLabelIds()).containsExactly("INBOX", "UNREAD");
        assertThat(summary.getSnippet()).isEqualTo("This is a test message snippet");
        assertThat(summary.getInternalDate()).isEqualTo(1234567890000L);
    }

    @Test
    @DisplayName("from() handles null fields in Message gracefully")
    void from_WithNullFields_CreatesMessageSummaryWithNullFields() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-789");
        // All other fields are null by default

        // When
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo("msg-789");
        assertThat(summary.getThreadId()).isNull();
        assertThat(summary.getLabelIds()).isNull();
        assertThat(summary.getSnippet()).isNull();
        assertThat(summary.getInternalDate()).isNull();
    }

    @Test
    @DisplayName("from() handles empty label list")
    void from_WithEmptyLabelList_CreatesMessageSummaryWithEmptyList() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-empty");
        gmailMessage.setLabelIds(List.of());
        gmailMessage.setSnippet("");

        // When
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo("msg-empty");
        assertThat(summary.getLabelIds()).isEmpty();
        assertThat(summary.getSnippet()).isEmpty();
    }

    @Test
    @DisplayName("getId() returns the message ID")
    void getId_ReturnsMessageId() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("test-id");
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // When
        String id = summary.getId();

        // Then
        assertThat(id).isEqualTo("test-id");
    }

    @Test
    @DisplayName("getThreadId() returns the thread ID")
    void getThreadId_ReturnsThreadId() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg");
        gmailMessage.setThreadId("thread-test");
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // When
        String threadId = summary.getThreadId();

        // Then
        assertThat(threadId).isEqualTo("thread-test");
    }

    @Test
    @DisplayName("getLabelIds() returns the label IDs list")
    void getLabelIds_ReturnsLabelIdsList() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg");
        List<String> labels = Arrays.asList("SENT", "IMPORTANT");
        gmailMessage.setLabelIds(labels);
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // When
        List<String> labelIds = summary.getLabelIds();

        // Then
        assertThat(labelIds).containsExactly("SENT", "IMPORTANT");
    }

    @Test
    @DisplayName("getSnippet() returns the message snippet")
    void getSnippet_ReturnsSnippet() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg");
        gmailMessage.setSnippet("Email preview text...");
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // When
        String snippet = summary.getSnippet();

        // Then
        assertThat(snippet).isEqualTo("Email preview text...");
    }

    @Test
    @DisplayName("getInternalDate() returns the internal date")
    void getInternalDate_ReturnsInternalDate() {
        // Given
        Message gmailMessage = new Message();
        gmailMessage.setId("msg");
        Long timestamp = 1609459200000L; // 2021-01-01
        gmailMessage.setInternalDate(timestamp);
        MessageSummary summary = MessageSummary.from(gmailMessage);

        // When
        Long internalDate = summary.getInternalDate();

        // Then
        assertThat(internalDate).isEqualTo(1609459200000L);
    }
}
