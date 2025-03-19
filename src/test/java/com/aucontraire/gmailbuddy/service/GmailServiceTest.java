package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailServiceTest {

    private GmailRepository gmailRepository;
    private GmailQueryBuilder gmailQueryBuilder;
    private GmailService gmailService;

    @BeforeEach
    void setUp() {
        gmailRepository = mock(GmailRepository.class);
        gmailQueryBuilder = mock(GmailQueryBuilder.class);
        gmailService = new GmailService(gmailRepository, gmailQueryBuilder);
    }

    @Test
    void testBuildQueryWithFilterCriteria() throws IOException {
        // Arrange
        String senderEmail = "test@example.com";
        FilterCriteria filterCriteria = mock(FilterCriteria.class);

        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(filterCriteria.getTo())).thenReturn("to: ");
        when(gmailQueryBuilder.subject(filterCriteria.getSubject())).thenReturn("subject: ");
        when(gmailQueryBuilder.hasAttachment(false)).thenReturn("has:attachment ");
        when(gmailQueryBuilder.query(filterCriteria.getQuery())).thenReturn("query:some-query ");
        when(gmailQueryBuilder.negatedQuery(filterCriteria.getNegatedQuery())).thenReturn("-query-to-exclude ");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("from:test@example.com to: subject: has:attachment query:some-query -query-to-exclude");

        // Act
        String result = gmailService.buildQuery(senderEmail, filterCriteria);

        // Assert
        assertEquals("from:test@example.com to: subject: has:attachment query:some-query -query-to-exclude", result);

        verify(gmailQueryBuilder).from(senderEmail);
        verify(gmailQueryBuilder).to(filterCriteria.getTo());
        verify(gmailQueryBuilder).subject(filterCriteria.getSubject());
        verify(gmailQueryBuilder).hasAttachment(false);
        verify(gmailQueryBuilder).query(filterCriteria.getQuery());
        verify(gmailQueryBuilder).negatedQuery(filterCriteria.getNegatedQuery());
    }

    @Test
    void testListMessagesSuccess() throws IOException, GmailServiceException {
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
        GmailServiceException exception = assertThrows(GmailServiceException.class, () -> gmailService.listMessages(userId));
        assertEquals("Failed to list messages for user: test-user", exception.getMessage());
        assertEquals(ioException, exception.getCause());
    }

    @Test
    void testDeleteMessagesFromSender() throws IOException, GmailServiceException {
        // Arrange
        String userId = "test-user";
        String senderEmail = "test@example.com";
        FilterCriteria filterCriteria = mock(FilterCriteria.class);

        String query = "from:test@example.com label:Inbox";
        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(query);

        // Act
        gmailService.deleteMessagesFromSender(userId, senderEmail, filterCriteria);

        // Assert
        verify(gmailRepository).deleteMessagesFromSender(eq(userId), eq(senderEmail), eq(query));
    }

    @Test
    void testModifyMessagesLabels() throws IOException, GmailServiceException {
        // Arrange
        String userId = "test-user";
        String senderEmail = "test@example.com";
        List<String> labelsToAdd = List.of("Important");
        List<String> labelsToRemove = List.of("Spam");

        // Mock query builder behavior
        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.query("label:Spam")).thenReturn("label:Spam");
        when(gmailQueryBuilder.build("from:test@example.com ", "label:Spam"))
                .thenReturn("from:test@example.com label:Spam");

        // Act: Call the method being tested
        gmailService.modifyMessagesLabels(userId, senderEmail, labelsToAdd, labelsToRemove);

        // Assert: Verify that mocks were correctly invoked
        verify(gmailQueryBuilder).from(senderEmail);
        verify(gmailQueryBuilder).query("label:Spam"); // Ensure this is invoked
        verify(gmailQueryBuilder).build("from:test@example.com ", "label:Spam"); // Ensure build() is invoked with proper arguments
        verify(gmailRepository).modifyMessagesLabels(eq(userId), eq(senderEmail), eq(labelsToAdd), eq(labelsToRemove), eq("from:test@example.com label:Spam"));
    }

    @Test
    void testGetMessageBodyReturnsMessageBody() throws IOException, GmailServiceException, MessageNotFoundException {
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
        GmailServiceException exception = assertThrows(GmailServiceException.class, () -> gmailService.getMessageBody(userId, messageId));
        assertEquals("Failed to get message body for messageId: test-message-id for user: test-user", exception.getMessage());
        assertEquals(ioException, exception.getCause());
    }
}
