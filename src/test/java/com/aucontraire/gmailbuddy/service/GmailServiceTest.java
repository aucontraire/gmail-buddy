package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.aucontraire.gmailbuddy.dto.FilterCriteriaWithLabelsDTO;
import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.aucontraire.gmailbuddy.exception.MessageNotFoundException;
import com.aucontraire.gmailbuddy.mapper.FilterCriteriaMapper;
import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.google.api.services.gmail.model.FilterCriteria;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
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
    void testDeleteMessagesByFilterCriteria() throws IOException, GmailServiceException {
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

        when(filterCriteriaMapper.toFilterCriteria(filterCriteriaDTO)).thenReturn(filterCriteria);

        when(gmailQueryBuilder.from("test@example.com")).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to("")).thenReturn("");
        when(gmailQueryBuilder.subject("")).thenReturn("");
        when(gmailQueryBuilder.hasAttachment(false)).thenReturn("");
        when(gmailQueryBuilder.query("label:Inbox")).thenReturn("label:Inbox");
        when(gmailQueryBuilder.negatedQuery("")).thenReturn("");
        when(gmailQueryBuilder.build("from:test@example.com ", "", "", "", "label:Inbox", ""))
                .thenReturn("from:test@example.com label:Inbox");

        // Act
        gmailService.deleteMessagesByFilterCriteria("test-user", filterCriteriaDTO);

        // Assert: Verify that the query was built and then passed to deleteMessagesByFilterCriteria.
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
    void testModifyMessagesLabels() throws IOException, GmailServiceException {
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
