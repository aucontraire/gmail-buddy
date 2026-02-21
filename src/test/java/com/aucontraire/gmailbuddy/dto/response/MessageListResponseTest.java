package com.aucontraire.gmailbuddy.dto.response;

import com.aucontraire.gmailbuddy.dto.common.ResponseMetadata;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageListResponse DTO.
 * Tests the builder pattern, pagination support, and metadata inclusion.
 */
@DisplayName("MessageListResponse Tests")
class MessageListResponseTest {

    @Test
    @DisplayName("builder creates valid response with all fields")
    void builder_WithAllFields_CreatesValidResponse() {
        // Given
        List<MessageSummary> messages = createTestMessageSummaries(3);
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(150L)
                .quotaUsed(5)
                .build();

        // When
        MessageListResponse response = MessageListResponse.builder()
                .messages(messages)
                .totalCount(100)
                .hasMore(true)
                .nextPageToken("next-token-123")
                .metadata(metadata)
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).hasSize(3);
        assertThat(response.getTotalCount()).isEqualTo(100);
        assertThat(response.getHasMore()).isTrue();
        assertThat(response.getNextPageToken()).isEqualTo("next-token-123");
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getDurationMs()).isEqualTo(150L);
        assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(5);
    }

    @Test
    @DisplayName("builder creates response with empty message list")
    void builder_WithEmptyMessageList_CreatesValidResponse() {
        // Given
        List<MessageSummary> emptyList = new ArrayList<>();

        // When
        MessageListResponse response = MessageListResponse.builder()
                .messages(emptyList)
                .totalCount(0)
                .hasMore(false)
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getHasMore()).isFalse();
    }

    @Test
    @DisplayName("builder creates response with null nextPageToken and hasMore false")
    void builder_WithNullNextPageToken_HasMoreIsFalse() {
        // Given
        List<MessageSummary> messages = createTestMessageSummaries(5);

        // When
        MessageListResponse response = MessageListResponse.builder()
                .messages(messages)
                .totalCount(5)
                .hasMore(false)
                .nextPageToken(null)
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).hasSize(5);
        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getHasMore()).isFalse();
        assertThat(response.getNextPageToken()).isNull();
    }

    @Test
    @DisplayName("builder creates response with metadata included")
    void builder_WithMetadata_IncludesMetadata() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(250L)
                .quotaUsed(10)
                .build();

        // When
        MessageListResponse response = MessageListResponse.builder()
                .messages(new ArrayList<>())
                .totalCount(0)
                .metadata(metadata)
                .build();

        // Then
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getDurationMs()).isEqualTo(250L);
        assertThat(response.getMetadata().getQuotaUsed()).isEqualTo(10);
        assertThat(response.getMetadata().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("builder allows chaining of method calls")
    void builder_AllowsMethodChaining() {
        // When
        MessageListResponse response = MessageListResponse.builder()
                .messages(createTestMessageSummaries(2))
                .totalCount(50)
                .hasMore(true)
                .nextPageToken("token")
                .metadata(ResponseMetadata.builder().durationMs(100L).build())
                .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(50);
        assertThat(response.getHasMore()).isTrue();
        assertThat(response.getNextPageToken()).isEqualTo("token");
        assertThat(response.getMetadata()).isNotNull();
    }

    @Test
    @DisplayName("getMessages() returns the message list")
    void getMessages_ReturnsMessageList() {
        // Given
        List<MessageSummary> messages = createTestMessageSummaries(4);
        MessageListResponse response = MessageListResponse.builder()
                .messages(messages)
                .build();

        // When
        List<MessageSummary> result = response.getMessages();

        // Then
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("getTotalCount() returns the total count")
    void getTotalCount_ReturnsTotalCount() {
        // Given
        MessageListResponse response = MessageListResponse.builder()
                .totalCount(42)
                .build();

        // When
        Integer totalCount = response.getTotalCount();

        // Then
        assertThat(totalCount).isEqualTo(42);
    }

    @Test
    @DisplayName("getHasMore() returns the hasMore flag")
    void getHasMore_ReturnsHasMoreFlag() {
        // Given
        MessageListResponse response = MessageListResponse.builder()
                .hasMore(true)
                .build();

        // When
        Boolean hasMore = response.getHasMore();

        // Then
        assertThat(hasMore).isTrue();
    }

    @Test
    @DisplayName("getNextPageToken() returns the next page token")
    void getNextPageToken_ReturnsNextPageToken() {
        // Given
        MessageListResponse response = MessageListResponse.builder()
                .nextPageToken("page-token-xyz")
                .build();

        // When
        String token = response.getNextPageToken();

        // Then
        assertThat(token).isEqualTo("page-token-xyz");
    }

    @Test
    @DisplayName("getMetadata() returns the response metadata")
    void getMetadata_ReturnsMetadata() {
        // Given
        ResponseMetadata metadata = ResponseMetadata.builder()
                .durationMs(300L)
                .quotaUsed(15)
                .build();
        MessageListResponse response = MessageListResponse.builder()
                .metadata(metadata)
                .build();

        // When
        ResponseMetadata result = response.getMetadata();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDurationMs()).isEqualTo(300L);
        assertThat(result.getQuotaUsed()).isEqualTo(15);
    }

    // Helper method to create test MessageSummary objects
    private List<MessageSummary> createTestMessageSummaries(int count) {
        List<MessageSummary> summaries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message message = new Message();
            message.setId("msg-" + i);
            message.setThreadId("thread-" + i);
            message.setLabelIds(Arrays.asList("INBOX"));
            message.setSnippet("Test message " + i);
            summaries.add(MessageSummary.from(message));
        }
        return summaries;
    }
}
