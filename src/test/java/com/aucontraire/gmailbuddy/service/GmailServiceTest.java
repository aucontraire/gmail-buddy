package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.repository.GmailRepository;
import com.aucontraire.gmailbuddy.service.GmailService;
import com.aucontraire.gmailbuddy.exception.GmailServiceException;
import com.google.api.services.gmail.model.FilterCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GmailServiceTest {

    @Mock
    private GmailRepository gmailRepository; // Mock repository

    @Mock
    private GmailQueryBuilder gmailQueryBuilder; // Mock query builder

    @InjectMocks
    private GmailService gmailService; // Inject mocks into service

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initialize mocks
    }

    @Test
    void testBuildQueryWithFilterCriteria() {
        // Arrange
        String senderEmail = "test@example.com";
        FilterCriteria filterCriteria = mock(FilterCriteria.class);

        // Mock the behavior of the `gmailQueryBuilder`
        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.to(filterCriteria.getTo())).thenReturn("to:recipient@example.com ");
        when(gmailQueryBuilder.subject(filterCriteria.getSubject())).thenReturn("subject:Test Subject ");
        when(gmailQueryBuilder.hasAttachment(filterCriteria.getHasAttachment())).thenReturn("has:attachment ");
        when(gmailQueryBuilder.query(filterCriteria.getQuery())).thenReturn("query:some-query ");
        when(gmailQueryBuilder.negatedQuery(filterCriteria.getNegatedQuery())).thenReturn("-query-to-exclude ");
        when(gmailQueryBuilder.build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("from:test@example.com to:recipient@example.com subject:Test Subject has:attachment query:some-query -query-to-exclude");

        // Act
        String result = gmailService.buildQuery(senderEmail, filterCriteria);

        // Assert
        assertEquals("from:test@example.com to:recipient@example.com subject:Test Subject has:attachment query:some-query -query-to-exclude", result);

        // Verify that each method on the builder was called appropriately
        verify(gmailQueryBuilder).from(eq(senderEmail));
        verify(gmailQueryBuilder).to(eq(filterCriteria.getTo()));
        verify(gmailQueryBuilder).subject(eq(filterCriteria.getSubject()));
        verify(gmailQueryBuilder).hasAttachment(eq(filterCriteria.getHasAttachment()));
        verify(gmailQueryBuilder).query(eq(filterCriteria.getQuery()));
        verify(gmailQueryBuilder).negatedQuery(eq(filterCriteria.getNegatedQuery()));
        verify(gmailQueryBuilder).build(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testBuildQueryWithLabelsToRemove() {
        // Arrange
        String senderEmail = "test@example.com";
        List<String> labelsToRemove = List.of("INBOX", "IMPORTANT");

        // Mock the behavior of the `gmailQueryBuilder`
        when(gmailQueryBuilder.from(senderEmail)).thenReturn("from:test@example.com ");
        when(gmailQueryBuilder.query(eq("label:INBOX AND label:IMPORTANT"))).thenReturn("label:INBOX AND label:IMPORTANT ");
        when(gmailQueryBuilder.build(anyString(), anyString()))
                .thenReturn("from:test@example.com label:INBOX AND label:IMPORTANT");

        // Act
        String result = gmailService.buildQuery(senderEmail, labelsToRemove);

        // Assert
        assertEquals("from:test@example.com label:INBOX AND label:IMPORTANT", result);

        // Verify that each method on the builder was called appropriately
        verify(gmailQueryBuilder).from(eq(senderEmail));
        verify(gmailQueryBuilder).query(eq("label:INBOX AND label:IMPORTANT"));
        verify(gmailQueryBuilder).build(anyString(), anyString());
    }
}
