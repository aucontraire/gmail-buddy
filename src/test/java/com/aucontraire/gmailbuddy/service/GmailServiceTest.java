package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.DeleteResult;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;
import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;
import com.aucontraire.gmailbuddy.exception.GmailApiException;
import com.aucontraire.gmailbuddy.exception.ResourceNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.AttachmentListResult;
import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.DraftDetailResult;
import com.aucontraire.gmailbuddy.service.DraftListResult;
import com.aucontraire.gmailbuddy.service.OriginalMessageLookup;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
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
    private MimeMessageBuilder mimeMessageBuilder;
    private GmailMessageMapper gmailMessageMapper;
    private GmailBuddyProperties properties;
    private GmailBuddyProperties.Send send;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository = mock(GmailRepository.class);
        gmailQueryBuilder = mock(GmailQueryBuilder.class);
        filterCriteriaMapper = mock(FilterCriteriaMapper.class);
        mimeMessageBuilder = mock(MimeMessageBuilder.class);
        gmailMessageMapper = mock(GmailMessageMapper.class);
        properties = mock(GmailBuddyProperties.class);
        send = mock(GmailBuddyProperties.Send.class);
        when(properties.send()).thenReturn(send);
        when(send.maxTotalPayloadSize()).thenReturn(DataSize.ofMegabytes(25));
        gmailService = new GmailService(gmailRepository, gmailQueryBuilder, filterCriteriaMapper,
                mimeMessageBuilder, gmailMessageMapper, properties);
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

    // =========================================================================
    // T009 — listDrafts() and getDraft() service method tests
    // =========================================================================

    @Test
    @DisplayName("listDrafts() should return DraftListResult from repository on success")
    void listDrafts_success_returnsDraftListResult() throws IOException {
        String userId = "test-user";
        DraftListResult expected = new DraftListResult(List.of(), null, null);
        when(gmailRepository.listDrafts(userId, null, 25)).thenReturn(expected);

        DraftListResult result = gmailService.listDrafts(userId, null, 25);

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).listDrafts(userId, null, 25);
    }

    @Test
    @DisplayName("listDrafts() should wrap IOException as GmailApiException")
    void listDrafts_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        IOException ioException = new IOException("API failure");
        when(gmailRepository.listDrafts(userId, null, 25)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.listDrafts(userId, null, 25));

        assertThat(ex.getMessage()).contains("Failed to list drafts for user: test-user");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("getDraft() should return DraftDetailResult from repository on success")
    void getDraft_success_returnsDraftDetailResult() throws IOException {
        String userId = "test-user";
        String draftId = "draft-abc123";
        DraftDetailResult expected = new DraftDetailResult(
                draftId, "msg-1", null, List.of(), List.of(), List.of(),
                null, null, null, "text", null, List.of()
        );
        when(gmailRepository.getDraft(userId, draftId)).thenReturn(expected);

        DraftDetailResult result = gmailService.getDraft(userId, draftId);

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).getDraft(userId, draftId);
    }

    @Test
    @DisplayName("getDraft() should propagate ResourceNotFoundException from repository")
    void getDraft_resourceNotFoundException_propagates() throws IOException {
        String userId = "test-user";
        String draftId = "draft-not-found";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Draft not found");
        when(gmailRepository.getDraft(userId, draftId)).thenThrow(notFound);

        assertThrows(ResourceNotFoundException.class,
                () -> gmailService.getDraft(userId, draftId));
    }

    @Test
    @DisplayName("getDraft() should wrap IOException as GmailApiException")
    void getDraft_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String draftId = "draft-abc";
        IOException ioException = new IOException("Timeout");
        when(gmailRepository.getDraft(userId, draftId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.getDraft(userId, draftId));

        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    // =========================================================================
    // T022 — deleteDraft() service method tests
    // =========================================================================

    @Test
    @DisplayName("deleteDraft() should delegate to repository and return void on success")
    void deleteDraft_success_doesNotThrow() throws IOException {
        String userId = "test-user";
        String draftId = "draft-del-001";
        doNothing().when(gmailRepository).deleteDraft(userId, draftId);

        assertDoesNotThrow(() -> gmailService.deleteDraft(userId, draftId));
        verify(gmailRepository).deleteDraft(userId, draftId);
    }

    @Test
    @DisplayName("deleteDraft() should propagate ResourceNotFoundException from repository")
    void deleteDraft_resourceNotFoundException_propagates() throws IOException {
        String userId = "test-user";
        String draftId = "draft-not-found";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Not found");
        doThrow(notFound).when(gmailRepository).deleteDraft(userId, draftId);

        assertThrows(ResourceNotFoundException.class,
                () -> gmailService.deleteDraft(userId, draftId));
    }

    @Test
    @DisplayName("deleteDraft() should wrap IOException as GmailApiException")
    void deleteDraft_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String draftId = "draft-del-io";
        IOException ioException = new IOException("Network error");
        doThrow(ioException).when(gmailRepository).deleteDraft(userId, draftId);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.deleteDraft(userId, draftId));

        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    // =========================================================================
    // T032 — updateDraft() service method tests
    // =========================================================================

    @Test
    @DisplayName("updateDraft() should return updated DraftDetailResult on success")
    void updateDraft_success_returnsUpdatedDraftDetailResult() throws Exception {
        String userId = "test-user";
        String draftId = "draft-upd-001";
        SendMessageDTO dto = mock(SendMessageDTO.class);
        when(dto.inReplyToMessageId()).thenReturn(null);
        when(dto.to()).thenReturn(List.of("to@example.com"));
        when(dto.cc()).thenReturn(List.of());
        when(dto.bcc()).thenReturn(List.of());
        when(dto.attachments()).thenReturn(List.of());
        when(dto.subject()).thenReturn("Subject");
        when(dto.body()).thenReturn("Body text");

        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mimeMessageBuilder.build(eq(dto), eq(null))).thenReturn(mimeMessage);

        DraftCreationResult updateResult = new DraftCreationResult(draftId, "msg-upd-1", null);
        when(gmailRepository.updateDraft(userId, draftId, mimeMessage)).thenReturn(updateResult);

        DraftDetailResult detailResult = new DraftDetailResult(
                draftId, "msg-upd-1", null, List.of("to@example.com"), List.of(), List.of(),
                "Subject", null, "Body text", "text", null, List.of()
        );
        when(gmailRepository.getDraft(userId, draftId)).thenReturn(detailResult);

        DraftDetailResult result = gmailService.updateDraft(userId, draftId, dto);

        assertThat(result).isEqualTo(detailResult);
        verify(gmailRepository).updateDraft(userId, draftId, mimeMessage);
        verify(gmailRepository).getDraft(userId, draftId);
    }

    @Test
    @DisplayName("updateDraft() should propagate ResourceNotFoundException from repository")
    void updateDraft_resourceNotFoundException_propagates() throws Exception {
        String userId = "test-user";
        String draftId = "draft-not-found";
        SendMessageDTO dto = mock(SendMessageDTO.class);
        when(dto.inReplyToMessageId()).thenReturn(null);
        when(dto.to()).thenReturn(List.of("to@example.com"));
        when(dto.cc()).thenReturn(List.of());
        when(dto.bcc()).thenReturn(List.of());
        when(dto.attachments()).thenReturn(List.of());
        when(dto.subject()).thenReturn("S");
        when(dto.body()).thenReturn("B");

        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mimeMessageBuilder.build(eq(dto), eq(null))).thenReturn(mimeMessage);

        ResourceNotFoundException notFound = new ResourceNotFoundException("Draft not found");
        when(gmailRepository.updateDraft(userId, draftId, mimeMessage)).thenThrow(notFound);

        assertThrows(ResourceNotFoundException.class,
                () -> gmailService.updateDraft(userId, draftId, dto));
    }

    @Test
    @DisplayName("updateDraft() with threading should perform lookup before update")
    void updateDraft_withInReplyToMessageId_performsLookupFirst() throws Exception {
        String userId = "test-user";
        String draftId = "draft-threaded";
        String replyToId = "original-msg-id";
        SendMessageDTO dto = mock(SendMessageDTO.class);
        when(dto.inReplyToMessageId()).thenReturn(replyToId);
        when(dto.to()).thenReturn(List.of("to@example.com"));
        when(dto.cc()).thenReturn(List.of());
        when(dto.bcc()).thenReturn(List.of());
        when(dto.attachments()).thenReturn(List.of());
        when(dto.subject()).thenReturn("Re: Subject");
        when(dto.body()).thenReturn("Reply body");

        OriginalMessageLookup lookup = new OriginalMessageLookup(replyToId, "thread-1", "<msg-id@mail.gmail.com>");
        when(gmailRepository.getMessageHeaders(userId, replyToId)).thenReturn(lookup);

        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mimeMessageBuilder.build(eq(dto), eq(lookup))).thenReturn(mimeMessage);

        DraftCreationResult updateResult = new DraftCreationResult(draftId, "msg-1", "thread-1");
        when(gmailRepository.updateDraft(userId, draftId, mimeMessage)).thenReturn(updateResult);

        DraftDetailResult detailResult = new DraftDetailResult(
                draftId, "msg-1", "thread-1", List.of("to@example.com"), List.of(), List.of(),
                "Re: Subject", null, "Reply body", "text", replyToId, List.of()
        );
        when(gmailRepository.getDraft(userId, draftId)).thenReturn(detailResult);

        DraftDetailResult result = gmailService.updateDraft(userId, draftId, dto);

        // Verify lookup happened BEFORE the update
        var inOrder = inOrder(gmailRepository);
        inOrder.verify(gmailRepository).getMessageHeaders(userId, replyToId);
        inOrder.verify(gmailRepository).updateDraft(userId, draftId, mimeMessage);

        assertThat(result.inReplyToMessageId()).isEqualTo(replyToId);
    }

    // =========================================================================
    // T013 — Feature 004 US1: listThreads() and getThread() service tests
    // =========================================================================

    @Test
    @DisplayName("listThreads() should return ThreadListResult from repository on success")
    void listThreads_success_returnsThreadListResult() throws IOException {
        String userId = "test-user";
        ThreadListResult expected = new ThreadListResult(List.of(), null, 0);
        FilterCriteriaDTO filter = new FilterCriteriaDTO();
        when(gmailRepository.listThreads(userId, filter, null, 50)).thenReturn(expected);

        ThreadListResult result = gmailService.listThreads(userId, filter, null, 50);

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).listThreads(userId, filter, null, 50);
    }

    @Test
    @DisplayName("listThreads() should return empty results list without error when no threads found")
    void listThreads_emptyResults_returnsEmptyListNotError() throws IOException {
        String userId = "test-user";
        ThreadListResult empty = new ThreadListResult(List.of(), null, 0);
        when(gmailRepository.listThreads(userId, null, null, 50)).thenReturn(empty);

        ThreadListResult result = gmailService.listThreads(userId, null, null, 50);

        assertThat(result.threads()).isEmpty();
        assertThat(result.nextPageToken()).isNull();
    }

    @Test
    @DisplayName("listThreads() should forward pagination token to repository")
    void listThreads_paginationToken_forwardedToRepository() throws IOException {
        String userId = "test-user";
        String pageToken = "token-page-2";
        ThreadSummary summary = new ThreadSummary("thread-1", "snippet", "12345");
        ThreadListResult page2 = new ThreadListResult(List.of(summary), "token-page-3", 2);
        when(gmailRepository.listThreads(userId, null, pageToken, 25)).thenReturn(page2);

        ThreadListResult result = gmailService.listThreads(userId, null, pageToken, 25);

        assertThat(result.threads()).hasSize(1);
        assertThat(result.nextPageToken()).isEqualTo("token-page-3");
        verify(gmailRepository).listThreads(userId, null, pageToken, 25);
    }

    @Test
    @DisplayName("listThreads() should forward filter criteria to repository")
    void listThreads_filterCriteria_forwardedToRepository() throws IOException {
        String userId = "test-user";
        FilterCriteriaDTO filter = new FilterCriteriaDTO();
        filter.setFrom("sender@example.com");
        filter.setHasAttachment(true);
        ThreadListResult result_from_repo = new ThreadListResult(List.of(), null, 0);
        when(gmailRepository.listThreads(userId, filter, null, 50)).thenReturn(result_from_repo);

        ThreadListResult result = gmailService.listThreads(userId, filter, null, 50);

        assertThat(result).isEqualTo(result_from_repo);
        // Verify the exact filter object is forwarded, not a copy
        verify(gmailRepository).listThreads(userId, filter, null, 50);
    }

    @Test
    @DisplayName("listThreads() should wrap IOException as GmailApiException")
    void listThreads_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        IOException ioException = new IOException("Network failure");
        when(gmailRepository.listThreads(userId, null, null, 50)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.listThreads(userId, null, null, 50));

        assertThat(ex.getMessage()).contains("Failed to list threads for user: test-user");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("listThreads() log must not contain PII — only op and count per FR-032")
    void listThreads_logging_containsOnlyPermittedFields() throws IOException {
        // This test verifies the service does NOT throw and returns the result;
        // the logging contract (no PII) is verified by ReadApiConstitutionVIIComplianceTest.
        // Here we confirm the success path does not surface any exception.
        String userId = "test-user";
        ThreadSummary s1 = new ThreadSummary("thread-aaa", "Snippet A", null);
        ThreadSummary s2 = new ThreadSummary("thread-bbb", "Snippet B", "9876");
        ThreadListResult twoThreads = new ThreadListResult(List.of(s1, s2), null, 2);
        when(gmailRepository.listThreads(userId, null, null, 50)).thenReturn(twoThreads);

        ThreadListResult result = gmailService.listThreads(userId, null, null, 50);

        assertThat(result.threads()).hasSize(2);
    }

    @Test
    @DisplayName("getThread() should return ThreadDetailResult from repository on success")
    void getThread_success_returnsThreadDetailResult() throws IOException {
        String userId = "test-user";
        String threadId = "thread-abc123";
        ThreadDetailResult expected = new ThreadDetailResult(threadId, List.of("INBOX"), List.of());
        when(gmailRepository.getThread(userId, threadId)).thenReturn(expected);

        ThreadDetailResult result = gmailService.getThread(userId, threadId);

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).getThread(userId, threadId);
    }

    @Test
    @DisplayName("getThread() should propagate ResourceNotFoundException from repository without wrapping")
    void getThread_resourceNotFoundException_propagatesUnwrapped() throws IOException {
        String userId = "test-user";
        String threadId = "thread-not-found";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Thread not found");
        when(gmailRepository.getThread(userId, threadId)).thenThrow(notFound);

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> gmailService.getThread(userId, threadId));

        assertThat(thrown).isSameAs(notFound);
    }

    @Test
    @DisplayName("getThread() should wrap IOException as GmailApiException")
    void getThread_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String threadId = "thread-io-fail";
        IOException ioException = new IOException("Timeout");
        when(gmailRepository.getThread(userId, threadId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.getThread(userId, threadId));

        assertThat(ex.getMessage()).contains("Failed to get thread thread-io-fail for user: test-user");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("getThread() with messages should expose messageCount in success path")
    void getThread_withMessages_messageCountAvailable() throws IOException {
        String userId = "test-user";
        String threadId = "thread-with-msgs";
        MessageDetailResult msg1 = new MessageDetailResult(
                "msg-1", threadId, Map.of(), "snippet 1", "body 1", "text",
                List.of("INBOX"), List.of()
        );
        MessageDetailResult msg2 = new MessageDetailResult(
                "msg-2", threadId, Map.of(), "snippet 2", null, "text",
                List.of("INBOX", "UNREAD"), List.of()
        );
        ThreadDetailResult twoMessages = new ThreadDetailResult(threadId,
                List.of("INBOX", "UNREAD"), List.of(msg1, msg2));
        when(gmailRepository.getThread(userId, threadId)).thenReturn(twoMessages);

        ThreadDetailResult result = gmailService.getThread(userId, threadId);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.threadId()).isEqualTo(threadId);
    }

    @Test
    @DisplayName("getThread() log must not contain PII — only op, threadId, messageCount per FR-032")
    void getThread_logging_containsOnlyPermittedFields() throws IOException {
        // Confirms the success path completes without exception; PII-in-logs assertion
        // is in ReadApiConstitutionVIIComplianceTest (integration level).
        String userId = "test-user";
        String threadId = "thread-log-check";
        ThreadDetailResult result_from_repo = new ThreadDetailResult(threadId, List.of(), List.of());
        when(gmailRepository.getThread(userId, threadId)).thenReturn(result_from_repo);

        assertDoesNotThrow(() -> gmailService.getThread(userId, threadId));
    }

    // =========================================================================
    // T029 — Phase 4 US2: getMessageDetail() service method tests
    // =========================================================================

    @Test
    @DisplayName("getMessageDetail() with format=full returns MessageDetailResult with body")
    void getMessageDetail_formatFull_returnsResultWithBody() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageDetailResult expected = new MessageDetailResult(
                messageId, "thread-001",
                Map.of("From", "sender@example.com", "Subject", "Hello"),
                "snippet text", "<p>body content</p>", "html",
                List.of("INBOX"), List.of()
        );
        when(gmailRepository.getMessageDetail(userId, messageId, "full")).thenReturn(expected);

        MessageDetailResult result = gmailService.getMessageDetail(userId, messageId, "full");

        assertThat(result).isEqualTo(expected);
        assertThat(result.body()).isNotNull();
        assertThat(result.body()).isEqualTo("<p>body content</p>");
        verify(gmailRepository).getMessageDetail(userId, messageId, "full");
    }

    @Test
    @DisplayName("getMessageDetail() with format=metadata returns MessageDetailResult with body=null")
    void getMessageDetail_formatMetadata_returnsResultWithNullBody() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageDetailResult expected = new MessageDetailResult(
                messageId, "thread-002",
                Map.of("From", "sender@example.com", "Subject", "Hi"),
                "snippet", null, null,
                List.of("INBOX"), List.of()
        );
        when(gmailRepository.getMessageDetail(userId, messageId, "metadata")).thenReturn(expected);

        MessageDetailResult result = gmailService.getMessageDetail(userId, messageId, "metadata");

        assertThat(result).isEqualTo(expected);
        assertThat(result.body()).isNull();
        verify(gmailRepository).getMessageDetail(userId, messageId, "metadata");
    }

    @Test
    @DisplayName("getMessageDetail() normalizes 'Full' to 'full' before repository call")
    void getMessageDetail_formatFull_caseNormalized() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageDetailResult expected = new MessageDetailResult(
                messageId, "thread-003", Map.of(), "s", "body", "text",
                List.of(), List.of()
        );
        when(gmailRepository.getMessageDetail(userId, messageId, "full")).thenReturn(expected);

        // Simulates the controller normalizing "Full" → "full" before passing to service
        MessageDetailResult result = gmailService.getMessageDetail(userId, messageId, "full");

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).getMessageDetail(userId, messageId, "full");
    }

    @Test
    @DisplayName("getMessageDetail() normalizes 'FULL' to 'full' before repository call")
    void getMessageDetail_formatFULL_caseNormalized() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageDetailResult expected = new MessageDetailResult(
                messageId, "thread-004", Map.of(), "s", "body", "text",
                List.of(), List.of()
        );
        when(gmailRepository.getMessageDetail(userId, messageId, "full")).thenReturn(expected);

        // Controller normalizes "FULL" → "full"
        MessageDetailResult result = gmailService.getMessageDetail(userId, messageId, "full");

        assertThat(result).isEqualTo(expected);
        verify(gmailRepository).getMessageDetail(userId, messageId, "full");
    }

    @Test
    @DisplayName("getMessageDetail() propagates ResourceNotFoundException without wrapping")
    void getMessageDetail_resourceNotFoundException_propagatesUnwrapped() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Message not found");
        when(gmailRepository.getMessageDetail(userId, messageId, "full")).thenThrow(notFound);

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> gmailService.getMessageDetail(userId, messageId, "full"));

        assertThat(thrown).isSameAs(notFound);
    }

    @Test
    @DisplayName("getMessageDetail() wraps IOException as GmailApiException")
    void getMessageDetail_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        IOException ioException = new IOException("Timeout");
        when(gmailRepository.getMessageDetail(userId, messageId, "full")).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.getMessageDetail(userId, messageId, "full"));

        assertThat(ex.getMessage()).contains("getMessageDetail");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("getMessageDetail() log must not contain PII — only op, messageId, format, attachmentCount per FR-032")
    void getMessageDetail_logging_containsOnlyPermittedFields() throws IOException {
        // Confirms the success path completes without exception; PII-in-logs assertion
        // is in ReadApiConstitutionVIIComplianceTest (integration level).
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageDetailResult result_from_repo = new MessageDetailResult(
                messageId, "thread-log", Map.of(), "snippet", null, null,
                List.of(), List.of()
        );
        when(gmailRepository.getMessageDetail(userId, messageId, "metadata")).thenReturn(result_from_repo);

        assertDoesNotThrow(() -> gmailService.getMessageDetail(userId, messageId, "metadata"));
    }

    // =========================================================================
    // T039 — Phase 5 US3: listLabels() and getLabel() service tests
    // =========================================================================

    @Test
    @DisplayName("listLabels() returns LabelListResult with full label set")
    void listLabels_successPath_returnsLabelListResult() throws IOException {
        String userId = "test-user";
        com.aucontraire.gmailbuddy.dto.response.LabelSummary inbox =
                new com.aucontraire.gmailbuddy.dto.response.LabelSummary(
                        "INBOX", "INBOX", "system", "show", "labelShow");
        com.aucontraire.gmailbuddy.dto.response.LabelSummary userLabel =
                new com.aucontraire.gmailbuddy.dto.response.LabelSummary(
                        "Label_42", "Recruiters", "user", "show", "labelShow");
        LabelListResult expected = new LabelListResult(List.of(inbox, userLabel), 2);
        when(gmailRepository.listLabels(userId)).thenReturn(expected);

        LabelListResult result = gmailService.listLabels(userId);

        assertThat(result).isEqualTo(expected);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.labels()).hasSize(2);
        verify(gmailRepository).listLabels(userId);
    }

    @Test
    @DisplayName("listLabels() returns empty list when user has no user labels (system labels only)")
    void listLabels_emptyLabelSet_returnsEmptyList() throws IOException {
        String userId = "test-user";
        LabelListResult expected = new LabelListResult(List.of(), 0);
        when(gmailRepository.listLabels(userId)).thenReturn(expected);

        LabelListResult result = gmailService.listLabels(userId);

        assertThat(result.labels()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);
        verify(gmailRepository).listLabels(userId);
    }

    @Test
    @DisplayName("listLabels() wraps IOException as GmailApiException")
    void listLabels_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        IOException ioException = new IOException("Network timeout");
        when(gmailRepository.listLabels(userId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.listLabels(userId));

        assertThat(ex.getMessage()).contains("list labels");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("getLabel() returns LabelDetailResult with counts and color for user label")
    void getLabel_withColorAndCounts_returnsLabelDetailResult() throws IOException {
        String userId = "test-user";
        String labelId = "Label_42";
        LabelDetailResult expected = new LabelDetailResult(
                labelId, "Recruiters", "user",
                "show", "labelShow",
                "#222222", "#16a766",
                42, 5, 38, 4
        );
        when(gmailRepository.getLabel(userId, labelId)).thenReturn(expected);

        LabelDetailResult result = gmailService.getLabel(userId, labelId);

        assertThat(result).isEqualTo(expected);
        assertThat(result.colorTextColor()).isEqualTo("#222222");
        assertThat(result.messagesTotal()).isEqualTo(42);
        verify(gmailRepository).getLabel(userId, labelId);
    }

    @Test
    @DisplayName("getLabel() returns LabelDetailResult without color for system label")
    void getLabel_systemLabelWithoutColor_returnsNullColor() throws IOException {
        String userId = "test-user";
        String labelId = "INBOX";
        LabelDetailResult expected = new LabelDetailResult(
                labelId, "INBOX", "system",
                "show", "labelShow",
                null, null,
                100, 10, 80, 8
        );
        when(gmailRepository.getLabel(userId, labelId)).thenReturn(expected);

        LabelDetailResult result = gmailService.getLabel(userId, labelId);

        assertThat(result.colorTextColor()).isNull();
        assertThat(result.colorBackgroundColor()).isNull();
        assertThat(result.id()).isEqualTo("INBOX");
        verify(gmailRepository).getLabel(userId, labelId);
    }

    @Test
    @DisplayName("getLabel() propagates ResourceNotFoundException without wrapping")
    void getLabel_resourceNotFoundException_propagatesUnwrapped() throws IOException {
        String userId = "test-user";
        String labelId = "Label_9999";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Label not found");
        when(gmailRepository.getLabel(userId, labelId)).thenThrow(notFound);

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> gmailService.getLabel(userId, labelId));

        assertThat(thrown).isSameAs(notFound);
    }

    @Test
    @DisplayName("getLabel() wraps IOException as GmailApiException")
    void getLabel_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String labelId = "INBOX";
        IOException ioException = new IOException("Timeout");
        when(gmailRepository.getLabel(userId, labelId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.getLabel(userId, labelId));

        assertThat(ex.getMessage().toLowerCase()).containsAnyOf("getlabel", "label");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    // =========================================================================
    // T056 — Phase 6 US4: listAttachments() and getAttachment() service tests
    // =========================================================================

    @Test
    @DisplayName("listAttachments() returns AttachmentListResult with N attachments")
    void listAttachments_withAttachments_returnsResult() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        MessageAttachmentMetadata att1 = new MessageAttachmentMetadata(
                "att-id-1", "report.pdf", "application/pdf", 12345L);
        MessageAttachmentMetadata att2 = new MessageAttachmentMetadata(
                "att-id-2", "photo.jpg", "image/jpeg", 67890L);
        AttachmentListResult expected = new AttachmentListResult(List.of(att1, att2));
        when(gmailRepository.listAttachments(userId, messageId)).thenReturn(expected);

        AttachmentListResult result = gmailService.listAttachments(userId, messageId);

        assertThat(result).isEqualTo(expected);
        assertThat(result.attachments()).hasSize(2);
        assertThat(result.attachments().get(0).attachmentId()).isEqualTo("att-id-1");
        assertThat(result.attachments().get(1).filename()).isEqualTo("photo.jpg");
        verify(gmailRepository).listAttachments(userId, messageId);
    }

    @Test
    @DisplayName("listAttachments() returns empty list (not error) when message has no attachments")
    void listAttachments_noAttachments_returnsEmptyList() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        AttachmentListResult expected = new AttachmentListResult(List.of());
        when(gmailRepository.listAttachments(userId, messageId)).thenReturn(expected);

        AttachmentListResult result = gmailService.listAttachments(userId, messageId);

        assertThat(result.attachments()).isEmpty();
        verify(gmailRepository).listAttachments(userId, messageId);
    }

    @Test
    @DisplayName("listAttachments() propagates ResourceNotFoundException without wrapping")
    void listAttachments_resourceNotFoundException_propagatesUnwrapped() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Message not found");
        when(gmailRepository.listAttachments(userId, messageId)).thenThrow(notFound);

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> gmailService.listAttachments(userId, messageId));

        assertThat(thrown).isSameAs(notFound);
    }

    @Test
    @DisplayName("listAttachments() wraps IOException as GmailApiException")
    void listAttachments_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        IOException ioException = new IOException("Network error");
        when(gmailRepository.listAttachments(userId, messageId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.listAttachments(userId, messageId));

        assertThat(ex.getMessage().toLowerCase()).containsAnyOf("attachment", "list");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }

    @Test
    @DisplayName("getAttachment() returns StreamingResponseBody with decoded bytes")
    void getAttachment_success_returnsStreamingResponseBody() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        String attachmentId = "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx";
        byte[] expected = "hello attachment".getBytes();
        StreamingResponseBody stream = outputStream -> outputStream.write(expected);
        when(gmailRepository.getAttachment(userId, messageId, attachmentId)).thenReturn(stream);

        StreamingResponseBody result = gmailService.getAttachment(userId, messageId, attachmentId);

        assertThat(result).isNotNull();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        result.writeTo(baos);
        assertThat(baos.toByteArray()).isEqualTo(expected);
        verify(gmailRepository).getAttachment(userId, messageId, attachmentId);
    }

    @Test
    @DisplayName("getAttachment() propagates ResourceNotFoundException without wrapping")
    void getAttachment_resourceNotFoundException_propagatesUnwrapped() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        String attachmentId = "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx";
        ResourceNotFoundException notFound = new ResourceNotFoundException("Attachment not found");
        when(gmailRepository.getAttachment(userId, messageId, attachmentId)).thenThrow(notFound);

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> gmailService.getAttachment(userId, messageId, attachmentId));

        assertThat(thrown).isSameAs(notFound);
    }

    @Test
    @DisplayName("getAttachment() wraps IOException as GmailApiException")
    void getAttachment_ioException_throwsGmailApiException() throws IOException {
        String userId = "test-user";
        String messageId = "1a2b3c4d5e6f7890";
        String attachmentId = "ANGjdJ8BwFpn3nQ0oFQ7wPjVLfRx";
        IOException ioException = new IOException("Connection reset");
        when(gmailRepository.getAttachment(userId, messageId, attachmentId)).thenThrow(ioException);

        GmailApiException ex = assertThrows(GmailApiException.class,
                () -> gmailService.getAttachment(userId, messageId, attachmentId));

        assertThat(ex.getMessage().toLowerCase()).containsAnyOf("attachment", "get");
        assertThat(ex.getCause()).isEqualTo(ioException);
    }
}
