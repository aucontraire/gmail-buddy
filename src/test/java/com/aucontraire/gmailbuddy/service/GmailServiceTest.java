package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GmailServiceTest {

    private GmailRepository gmailRepository;
    private GmailQueryBuilder gmailQueryBuilder;
    private FilterCriteriaMapper filterCriteriaMapper;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository = mock(GmailRepository.class);
        gmailQueryBuilder = mock(GmailQueryBuilder.class);
        filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        gmailService = new GmailService(gmailRepository, gmailQueryBuilder, filterCriteriaMapper);
    }

    @Test
    void testBuildQueryWithFilterCriteria() {
        // Arrange: create a real instance of FilterCriteria with desired values.
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setFrom("test@example.com");
        filterCriteria.setTo("");
        filterCriteria.setSubject("");
        filterCriteria.setHasAttachment(false);
        filterCriteria.setQuery("some-query");
        filterCriteria.setNegatedQuery("-query-to-exclude");

        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to("")).thenReturn("to: ");
        when(gmailQueryBuilder.subject("")).thenReturn("subject: ");
        when(gmailQueryBuilder.hasAttachment(false)).thenReturn("has:attachment ");
        when(gmailQueryBuilder.query("some-query")).thenReturn("query:some-query ");
        when(gmailQueryBuilder.negatedQuery("-query-to-exclude")).thenReturn("-query-to-exclude ");
        when(gmailQueryBuilder.build("from:test@example.com ", "to: ", "subject: ", "has:attachment ", "query:some-query ", "-query-to-exclude "))
                .thenReturn("from:test@example.com to: subject: has:attachment query:some-query -query-to-exclude");

        // Act: call the new buildQuery method which accepts FilterCriteria.
        String result = gmailService.buildQuery(filterCriteria);

        // Assert
        assertEquals("from:test@example.com to: subject: has:attachment query:some-query -query-to-exclude", result);
        verify(gmailQueryBuilder).from("test@example.com");
        verify(gmailQueryBuilder).to("");
        verify(gmailQueryBuilder).subject("");
        verify(gmailQueryBuilder).hasAttachment(false);
        verify(gmailQueryBuilder).query("some-query");
        verify(gmailQueryBuilder).negatedQuery("-query-to-exclude");
        verify(gmailQueryBuilder).build("from:test@example.com ", "to: ", "subject: ", "has:attachment ", "query:some-query ", "-query-to-exclude ");
    }

    @Test
    void testListMessagesSuccess() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        List<Message> mockMessages = List.of(new Message(), new Message());
        when(gmailRepository.getMessages(userId)).thenReturn(mockMessages);

        // Act
        List<Message> result = gmailService.listMessages(userId);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void testListMessagesThrowsException() throws IOException {
        // Arrange
        String userId = "test-user";
        IOException ioException = new IOException("Failed to list messages");
        when(gmailRepository.getMessages(userId)).thenThrow(ioException);

        // Act & Assert
        GmailApiException exception = assertThrows(GmailApiException.class, () -> gmailService.listMessages(userId));
        assertEquals("Failed to list messages for user: test-user", exception.getMessage());
        assertEquals(ioException, exception.getCause());
    }

    @Test
    void testDeleteMessagesByFilterCriteria() throws IOException, GmailApiException {
        // Arrange: Create a real FilterCriteria and corresponding DTO.
        FilterCriteria filterCriteria = new FilterCriteria();
        filterCriteria.setFrom("test@example.com");
        filterCriteria.setTo("");
        filterCriteria.setSubject("");
        filterCriteria.setHasAttachment(false);
        filterCriteria.setQuery("label:Inbox");
        filterCriteria.setNegatedQuery("");

        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("test@example.com");
        filterCriteriaDTO.setTo("");
        filterCriteriaDTO.setSubject("");
        filterCriteriaDTO.setHasAttachment(false);
        filterCriteriaDTO.setQuery("label:Inbox");
        filterCriteriaDTO.setNegatedQuery("");

        // Create a BulkOperationResult for the repository to return
        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess("msg1");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(filterCriteria);

        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to("")).thenReturn("");
        when(gmailQueryBuilder.subject("")).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(false)).thenReturn("");
        when(gmailQueryBuilder.query("label:Inbox")).thenReturn("label:Inbox");
        when(gmailQueryBuilder.negatedQuery("")).thenReturn("");
        when(gmailQueryBuilder.build("from:test@example.com ", "", "", "", "label:Inbox", ""))
                .thenReturn("from:test@example.com label:Inbox");
        when(gmailRepository.deleteMessagesByFilterCriteria("test-user", "from:test@example.com label:Inbox"))
                .thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria("test-user", filterCriteriaDTO);

        // Assert: Verify that the query was built and then passed to deleteMessagesByFilterCriteria.
        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        verify(gmailQueryBuilder).from("test@example.com");
        verify(gmailQueryBuilder).to("");
        verify(gmailQueryBuilder).subject("");
        verify(gmailQueryBuilder).hasAttachment(false);
        verify(gmailQueryBuilder).query("label:Inbox");
        verify(gmailQueryBuilder).negatedQuery("");
        verify(gmailQueryBuilder).build("from:test@example.com ", "", "", "", "label:Inbox", "");
        verify(gmailRepository).deleteMessagesByFilterCriteria("test-user", "from:test@example.com label:Inbox");
    }

    @Test
    void testModifyMessagesLabels() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        String senderEmail = "test@example.com";
        FilterCriteriaWithLabelsDTO dto = new FilterCriteriaWithLabelsDTO();
        dto.setFrom(senderEmail);
        dto.setLabelsToAdd(Collections.singletonList("Important"));
        dto.setLabelsToRemove(Collections.singletonList("Spam"));
        List<String> labelsToAdd = List.of("Important");
        List<String> labelsToRemove = List.of("Spam");

        // Stub calls related to the new buildQuery(String, List) flow
        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.query("label:Spam")).thenReturn("label:Spam");
        when(gmailQueryBuilder.build("from:test@example.com ", "label:Spam"))
                .thenReturn("from:test@example.com label:Spam");

        // Act
        gmailService.modifyMessagesLabelsByFilterCriteria(userId, dto);

        // Assert
        verify(gmailQueryBuilder).from(senderEmail);
        verify(gmailQueryBuilder).query("label:Spam");
        verify(gmailQueryBuilder).build("from:test@example.com ", "label:Spam");
        verify(gmailRepository).modifyMessagesLabels(
                userId,
                labelsToAdd,
                labelsToRemove,
                "from:test@example.com label:Spam"
        );
    }

    @Test
    void testGetMessageBodyReturnsMessageBody() throws IOException, GmailApiException, ResourceNotFoundException {
        // Arrange
        String userId = "test-user";
        String messageId = "test-message-id";
        when(gmailRepository.getMessageBody(userId, messageId)).thenReturn("Test Message Body");

        // Act
        String result = gmailService.getMessageBody(userId, messageId);

        // Assert
        assertEquals("Test Message Body", result);
    }

    @Test
    void testGetMessageBodyThrowsException() throws IOException {
        // Arrange
        String userId = "test-user";
        String messageId = "test-message-id";
        IOException ioException = new IOException("Failed to get message body");
        when(gmailRepository.getMessageBody(userId, messageId)).thenThrow(ioException);

        // Act & Assert
        GmailApiException exception = assertThrows(GmailApiException.class, () -> gmailService.getMessageBody(userId, messageId));
        assertEquals("Failed to get message body for messageId: test-message-id for user: test-user", exception.getMessage());
        assertEquals(ioException, exception.getCause());
    }

    // ========== Tests for deleteMessage() - Stage 3: JSON Response ==========

    @Test
    @DisplayName("deleteMessage() should return DeleteResult with success=true when deletion succeeds")
    void deleteMessage_SuccessfulDeletion_ReturnsSuccessResult() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        String messageId = "msg12345";
        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess(messageId);
        bulkResult.markCompleted();

        when(gmailRepository.deleteMessage(userId, messageId)).thenReturn(bulkResult);

        // Act
        DeleteResult result = gmailService.deleteMessage(userId, messageId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Message deleted successfully");
        verify(gmailRepository).deleteMessage(userId, messageId);
    }

    @Test
    @DisplayName("deleteMessage() should return DeleteResult with success=false when deletion fails")
    void deleteMessage_FailedDeletion_ReturnsFailureResult() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        String messageId = "msg12345";
        String errorMessage = "Message not found";
        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addFailure(messageId, errorMessage);
        bulkResult.markCompleted();

        when(gmailRepository.deleteMessage(userId, messageId)).thenReturn(bulkResult);

        // Act
        DeleteResult result = gmailService.deleteMessage(userId, messageId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(errorMessage);
        verify(gmailRepository).deleteMessage(userId, messageId);
    }

    @Test
    @DisplayName("deleteMessage() should handle edge case where no result is recorded")
    void deleteMessage_NoResultRecorded_ReturnsFailureWithUnknownError() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        String messageId = "msg12345";
        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        // Don't add any success or failure - edge case

        when(gmailRepository.deleteMessage(userId, messageId)).thenReturn(bulkResult);

        // Act
        DeleteResult result = gmailService.deleteMessage(userId, messageId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Unknown error - no result recorded");
        verify(gmailRepository).deleteMessage(userId, messageId);
    }

    @Test
    @DisplayName("deleteMessage() should throw GmailApiException when IOException occurs")
    void deleteMessage_IOExceptionOccurs_ThrowsGmailApiException() throws IOException {
        // Arrange
        String userId = "test-user";
        String messageId = "msg12345";
        IOException ioException = new IOException("Network error");

        when(gmailRepository.deleteMessage(userId, messageId)).thenThrow(ioException);

        // Act & Assert
        GmailApiException exception = assertThrows(
                GmailApiException.class,
                () -> gmailService.deleteMessage(userId, messageId)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Failed to delete message for messageId: msg12345 for user: test-user");
        assertThat(exception.getCause()).isEqualTo(ioException);
        verify(gmailRepository).deleteMessage(userId, messageId);
    }

    @Test
    @DisplayName("deleteMessage() should handle null or empty messageId gracefully")
    void deleteMessage_NullOrEmptyMessageId_HandlesGracefully() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        String messageId = "";
        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess(messageId);

        when(gmailRepository.deleteMessage(userId, messageId)).thenReturn(bulkResult);

        // Act
        DeleteResult result = gmailService.deleteMessage(userId, messageId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEmpty();
        assertThat(result.isSuccess()).isTrue();
    }

    // ========== Tests for deleteMessagesByFilterCriteria() - Stage 3: JSON Response ==========

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should return BulkOperationResult with all successes")
    void deleteMessagesByFilterCriteria_AllSuccessful_ReturnsBulkResultWithSuccesses() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("test@example.com");
        filterCriteriaDTO.setQuery("label:Inbox");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("test@example.com");
        criteria.setQuery("label:Inbox");

        String expectedQuery = "from:test@example.com label:Inbox";

        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess("msg1");
        bulkResult.addSuccess("msg2");
        bulkResult.addSuccess("msg3");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(null)).thenReturn("");
        when(gmailQueryBuilder.subject(null)).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(null)).thenReturn("");
        when(gmailQueryBuilder.query("label:Inbox")).thenReturn("label:Inbox");
        when(gmailQueryBuilder.negatedQuery(null)).thenReturn("");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expectedQuery);
        when(gmailRepository.deleteMessagesByFilterCriteria(userId, expectedQuery)).thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.isCompleteSuccess()).isTrue();
        assertThat(result.getSuccessfulOperations()).containsExactly("msg1", "msg2", "msg3");
        verify(gmailRepository).deleteMessagesByFilterCriteria(userId, expectedQuery);
    }

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should return BulkOperationResult with partial success")
    void deleteMessagesByFilterCriteria_PartialSuccess_ReturnsBulkResultWithMixedResults() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("test@example.com");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("test@example.com");

        String expectedQuery = "from:test@example.com";

        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess("msg1");
        bulkResult.addSuccess("msg2");
        bulkResult.addFailure("msg3", "Permission denied");
        bulkResult.addFailure("msg4", "Message not found");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(null)).thenReturn("");
        when(gmailQueryBuilder.subject(null)).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(null)).thenReturn("");
        when(gmailQueryBuilder.query(null)).thenReturn("");
        when(gmailQueryBuilder.negatedQuery(null)).thenReturn("");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expectedQuery);
        when(gmailRepository.deleteMessagesByFilterCriteria(userId, expectedQuery)).thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(4);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.isCompleteSuccess()).isFalse();
        assertThat(result.hasSuccesses()).isTrue();
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getSuccessRate()).isEqualTo(50.0);
        assertThat(result.getFailedOperations())
                .containsEntry("msg3", "Permission denied")
                .containsEntry("msg4", "Message not found");
    }

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should return BulkOperationResult with all failures")
    void deleteMessagesByFilterCriteria_AllFailed_ReturnsBulkResultWithOnlyFailures() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("test@example.com");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("test@example.com");

        String expectedQuery = "from:test@example.com";

        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addFailure("msg1", "Rate limit exceeded");
        bulkResult.addFailure("msg2", "Rate limit exceeded");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(null)).thenReturn("");
        when(gmailQueryBuilder.subject(null)).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(null)).thenReturn("");
        when(gmailQueryBuilder.query(null)).thenReturn("");
        when(gmailQueryBuilder.negatedQuery(null)).thenReturn("");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expectedQuery);
        when(gmailRepository.deleteMessagesByFilterCriteria(userId, expectedQuery)).thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.isCompleteSuccess()).isFalse();
        assertThat(result.hasSuccesses()).isFalse();
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getSuccessRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should handle empty results (no messages found)")
    void deleteMessagesByFilterCriteria_EmptyResults_ReturnsEmptyBulkResult() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("nonexistent@example.com");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("nonexistent@example.com");

        String expectedQuery = "from:nonexistent@example.com";

        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("nonexistent@example.com")).thenReturn("from:nonexistent@example.com ");
        when(gmailQueryBuilder.to(null)).thenReturn("");
        when(gmailQueryBuilder.subject(null)).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(null)).thenReturn("");
        when(gmailQueryBuilder.query(null)).thenReturn("");
        when(gmailQueryBuilder.negatedQuery(null)).thenReturn("");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expectedQuery);
        when(gmailRepository.deleteMessagesByFilterCriteria(userId, expectedQuery)).thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotalOperations()).isEqualTo(0);
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.isCompleteSuccess()).isFalse(); // No successes
        verify(gmailRepository).deleteMessagesByFilterCriteria(userId, expectedQuery);
    }

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should throw GmailApiException when IOException occurs")
    void deleteMessagesByFilterCriteria_IOExceptionOccurs_ThrowsGmailApiException() throws IOException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("test@example.com");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("test@example.com");

        IOException ioException = new IOException("Network timeout");

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(null)).thenReturn("");
        when(gmailQueryBuilder.subject(null)).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(null)).thenReturn("");
        when(gmailQueryBuilder.query(null)).thenReturn("");
        when(gmailQueryBuilder.negatedQuery(null)).thenReturn("");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("from:test@example.com");
        when(gmailRepository.deleteMessagesByFilterCriteria(eq(userId), anyString())).thenThrow(ioException);

        // Act & Assert
        GmailApiException exception = assertThrows(
                GmailApiException.class,
                () -> gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO)
        );

        assertThat(exception.getMessage()).contains("Failed to delete messages for user: test-user");
        assertThat(exception.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("deleteMessagesByFilterCriteria() should properly build query from FilterCriteriaDTO")
    void deleteMessagesByFilterCriteria_ProperlyBuildsQuery() throws IOException, GmailApiException {
        // Arrange
        String userId = "test-user";
        FilterCriteriaDTO filterCriteriaDTO = new FilterCriteriaDTO();
        filterCriteriaDTO.setFrom("sender@example.com");
        filterCriteriaDTO.setTo("recipient@example.com");
        filterCriteriaDTO.setSubject("Important");
        filterCriteriaDTO.setHasAttachment(true);
        filterCriteriaDTO.setQuery("label:Inbox");
        filterCriteriaDTO.setNegatedQuery("label:Spam");

        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom("sender@example.com");
        criteria.setTo("recipient@example.com");
        criteria.setSubject("Important");
        criteria.setHasAttachment(true);
        criteria.setQuery("label:Inbox");
        criteria.setNegatedQuery("label:Spam");

        String expectedQuery = "from:sender@example.com to:recipient@example.com subject:Important has:attachment label:Inbox -label:Spam";

        BulkOperationResult bulkResult = new BulkOperationResult("DELETE");
        bulkResult.addSuccess("msg1");
        bulkResult.markCompleted();

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(criteria);
        when(gmailQueryBuilder.from("sender@example.com")).thenReturn("from:sender@example.com ");
        when(gmailQueryBuilder.to("recipient@example.com")).thenReturn("to:recipient@example.com ");
        when(gmailQueryBuilder.subject("Important")).thenReturn("subject:Important ");
        when(gmailQueryBuilder.hasAttachment(true)).thenReturn("has:attachment ");
        when(gmailQueryBuilder.query("label:Inbox")).thenReturn("label:Inbox ");
        when(gmailQueryBuilder.negatedQuery("label:Spam")).thenReturn("-label:Spam ");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(expectedQuery);
        when(gmailRepository.deleteMessagesByFilterCriteria(userId, expectedQuery)).thenReturn(bulkResult);

        // Act
        BulkOperationResult result = gmailService.deleteMessagesByFilterCriteria(userId, filterCriteriaDTO);

        // Assert
        verify(gmailQueryBuilder).from("sender@example.com");
        verify(gmailQueryBuilder).to("recipient@example.com");
        verify(gmailQueryBuilder).subject("Important");
        verify(gmailQueryBuilder).hasAttachment(true);
        verify(gmailQueryBuilder).query("label:Inbox");
        verify(gmailQueryBuilder).negatedQuery("label:Spam");
        verify(gmailRepository).deleteMessagesByFilterCriteria(userId, expectedQuery);
    }
}
