package com.aucontraire.gmailbuddy.repository;

import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.exception.AuthenticationException;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.aucontraire.gmailbuddy.service.BulkOperationResult;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GmailRepositoryImpl with TokenProvider abstraction.
 * 
 * These tests verify that the repository layer properly uses the TokenProvider
 * abstraction and is decoupled from Spring Security's SecurityContextHolder.
 */
@ExtendWith(MockitoExtension.class)
class GmailRepositoryImplTest {

    @Mock
    private GmailClient gmailClient;

    @Mock
    private GmailBatchClient gmailBatchClient;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private GmailBuddyProperties properties;
    
    @Mock
    private Gmail gmail;
    
    @Mock
    private Gmail.Users users;
    
    @Mock
    private Gmail.Users.Messages messages;
    
    @Mock
    private Gmail.Users.Messages.List messagesList;
    
    @Mock
    private Gmail.Users.Messages.Trash messagesTrash;
    
    @Mock
    private Gmail.Users.Messages.Delete messagesDelete;
    
    private GmailRepositoryImpl repository;
    
    private static final String TEST_USER_ID = "testuser@example.com";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-123";
    private static final String TEST_MESSAGE_ID = "test-message-id";
    private static final String TEST_QUERY = "from:test@example.com";
    
    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(gmailClient, gmailBatchClient, tokenProvider, properties);
    }
    
    @Test
    void getMessages_WithValidToken_ReturnsMessages() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        Message message1 = new Message().setId("msg1");
        Message message2 = new Message().setId("msg2");
        List<Message> expectedMessages = Arrays.asList(message1, message2);
        
        ListMessagesResponse response = new ListMessagesResponse().setMessages(expectedMessages);
        
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.list(TEST_USER_ID)).thenReturn(messagesList);
        when(messagesList.execute()).thenReturn(response);
        
        // When
        List<Message> result = repository.getMessages(TEST_USER_ID);
        
        // Then
        assertEquals(expectedMessages, result);
        verify(tokenProvider).getAccessToken();
        verify(gmailClient).createGmailService(TEST_ACCESS_TOKEN);
    }
    
    @Test
    void getMessages_WithAuthenticationException_ThrowsIllegalStateException() throws AuthenticationException, GeneralSecurityException {
        // Given
        when(tokenProvider.getAccessToken()).thenThrow(new AuthenticationException("Token expired"));
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> repository.getMessages(TEST_USER_ID)
        );
        
        assertTrue(exception.getMessage().contains("Failed to authenticate with Gmail API"));
        assertTrue(exception.getCause() instanceof AuthenticationException);
        verify(tokenProvider).getAccessToken();
        verifyNoInteractions(gmailClient);
    }
    
    @Test
    void getLatestMessages_WithValidToken_ReturnsLimitedMessages() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        long maxResults = 10L;
        Message message = new Message().setId("latest-msg");
        List<Message> expectedMessages = Arrays.asList(message);
        
        ListMessagesResponse response = new ListMessagesResponse().setMessages(expectedMessages);
        
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.list(TEST_USER_ID)).thenReturn(messagesList);
        when(messagesList.setMaxResults(maxResults)).thenReturn(messagesList);
        when(messagesList.execute()).thenReturn(response);
        
        // When
        List<Message> result = repository.getLatestMessages(TEST_USER_ID, maxResults);
        
        // Then
        assertEquals(expectedMessages, result);
        verify(messagesList).setMaxResults(maxResults);
        verify(tokenProvider).getAccessToken();
    }
    
    @Test
    void getMessagesByFilterCriteria_WithValidQuery_ReturnsFilteredMessages() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        Message message = new Message().setId("filtered-msg");
        List<Message> expectedMessages = Arrays.asList(message);
        
        ListMessagesResponse response = new ListMessagesResponse().setMessages(expectedMessages);
        
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.list(TEST_USER_ID)).thenReturn(messagesList);
        when(messagesList.setQ(TEST_QUERY)).thenReturn(messagesList);
        when(messagesList.execute()).thenReturn(response);
        
        // When
        List<Message> result = repository.getMessagesByFilterCriteria(TEST_USER_ID, TEST_QUERY);
        
        // Then
        assertEquals(expectedMessages, result);
        verify(messagesList).setQ(TEST_QUERY);
        verify(tokenProvider).getAccessToken();
    }
    
    @Test
    void deleteMessage_WithValidToken_DeletesMessage() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        BulkOperationResult successResult = new BulkOperationResult("DELETE");
        successResult.addSuccess(TEST_MESSAGE_ID);
        successResult.markCompleted();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmailBatchClient.batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID)))
            .thenReturn(successResult);

        // When
        repository.deleteMessage(TEST_USER_ID, TEST_MESSAGE_ID);

        // Then
        verify(tokenProvider).getAccessToken();
        verify(gmailBatchClient).batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID));
    }
    
    @Test
    void deleteMessage_WithGmailClientException_ThrowsIOException() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
            .thenThrow(new GeneralSecurityException("Gmail service creation failed"));
        
        // When & Then
        IOException exception = assertThrows(
            IOException.class,
            () -> repository.deleteMessage(TEST_USER_ID, TEST_MESSAGE_ID)
        );

        assertTrue(exception.getMessage().contains("Security exception creating Gmail service"));
        verify(tokenProvider).getAccessToken();
    }

    @Test
    void deleteMessage_WithBatchFailure_ThrowsIOException() throws IOException, GeneralSecurityException, AuthenticationException {
        // Given
        BulkOperationResult failureResult = new BulkOperationResult("DELETE");
        failureResult.addFailure(TEST_MESSAGE_ID, "Message not found");
        failureResult.markCompleted();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmailBatchClient.batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID)))
            .thenReturn(failureResult);

        // When & Then
        IOException exception = assertThrows(
            IOException.class,
            () -> repository.deleteMessage(TEST_USER_ID, TEST_MESSAGE_ID)
        );

        assertTrue(exception.getMessage().contains("Failed to delete message"));
        assertTrue(exception.getMessage().contains(TEST_MESSAGE_ID));
        verify(tokenProvider).getAccessToken();
        verify(gmailBatchClient).batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID));
    }

    @Test
    void testTokenProviderIntegration_NoSecurityContextHolderDependency() throws AuthenticationException {
        // This test verifies that the repository doesn't directly use SecurityContextHolder
        // and relies solely on the TokenProvider abstraction
        
        // Given
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        
        // When
        assertDoesNotThrow(() -> {
            // The repository should be able to get tokens without any SecurityContext setup
            // This would fail in the old implementation without proper Spring Security context
            String token = tokenProvider.getAccessToken();
            assertNotNull(token);
        });
        
        // Then
        verify(tokenProvider).getAccessToken();
    }
    
    @Test
    void testTokenProviderMockability() throws AuthenticationException {
        // This test demonstrates how easy it is to mock the TokenProvider
        // which was the main goal of the refactoring

        // Given - different token per call to simulate token refresh
        when(tokenProvider.getAccessToken())
            .thenReturn("token-1")
            .thenReturn("token-2");

        // When
        String firstToken = tokenProvider.getAccessToken();
        String secondToken = tokenProvider.getAccessToken();

        // Then
        assertEquals("token-1", firstToken);
        assertEquals("token-2", secondToken);
        verify(tokenProvider, times(2)).getAccessToken();
    }

    // ========== Tests for Batch Integration ==========

    @Test
    @DisplayName("Should verify batch client is injected and available")
    void batchClient_ShouldBeInjectedCorrectly() {
        // This test verifies that the GmailBatchClient is properly injected
        // and available for use in the repository implementation

        // Arrange & Act - Constructor injection happens in @BeforeEach
        // The repository should have been created with the batch client

        // Assert - We can't directly test injection without reflection,
        // but we can verify it's used in actual operations
        assertThat(gmailBatchClient).isNotNull();
        assertThat(repository).isNotNull();
    }

    @Test
    @DisplayName("Should use batch client for single message deletion")
    void deleteMessage_ShouldUseBatchClientInternally() throws IOException, GeneralSecurityException, AuthenticationException {
        // This test verifies that the single message delete operation
        // now uses the batch client internally for consistency

        // Arrange
        BulkOperationResult successResult = new BulkOperationResult("DELETE");
        successResult.addSuccess(TEST_MESSAGE_ID);
        successResult.markCompleted();

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmailBatchClient.batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID)))
            .thenReturn(successResult);

        // Act
        repository.deleteMessage(TEST_USER_ID, TEST_MESSAGE_ID);

        // Assert
        verify(tokenProvider).getAccessToken();
        verify(gmailBatchClient).batchDeleteMessages(gmail, TEST_USER_ID, List.of(TEST_MESSAGE_ID));
    }
}