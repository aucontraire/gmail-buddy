package com.aucontraire.gmailbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aucontraire.gmailbuddy.client.GmailBatchClient;
import com.aucontraire.gmailbuddy.client.GmailClient;
import com.aucontraire.gmailbuddy.config.GmailBuddyProperties;
import com.aucontraire.gmailbuddy.dto.response.LabelSummary;
import com.aucontraire.gmailbuddy.exception.LabelAlreadyExistsException;
import com.aucontraire.gmailbuddy.mapper.GmailMessageMapper;
import com.aucontraire.gmailbuddy.service.GmailQueryBuilder;
import com.aucontraire.gmailbuddy.service.TokenProvider;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link GmailRepositoryImpl#createLabel(String, String, String, String)}.
 *
 * <p>Exercises the Gmail API mock chain:
 * {@code Gmail → Users → Labels → Create → execute()}.
 * Verifies success mapping via {@link GmailMessageMapper#toLabelSummary}, and the
 * 409-to-{@link LabelAlreadyExistsException} translation (feature 005 US3, FR-010/FR-015).</p>
 *
 * <p>Each test follows Arrange-Act-Assert with clearly separated sections, mirroring
 * {@code GmailRepositoryImplSendDraftTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GmailRepositoryImpl — createLabel")
class GmailRepositoryImplCreateLabelTest {

    // -------------------------------------------------------------------------
    // Standard test constants
    // -------------------------------------------------------------------------

    private static final String TEST_USER_ID = "me";
    private static final String TEST_ACCESS_TOKEN = "test-access-token-xyz";
    private static final String TEST_LABEL_ID = "Label_123";
    private static final String TEST_LABEL_NAME = "pending-purge";

    // -------------------------------------------------------------------------
    // Mocks for the full Gmail service dependency chain
    // -------------------------------------------------------------------------

    @Mock
    private GmailClient gmailClient;

    @Mock
    private GmailBatchClient gmailBatchClient;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private GmailBuddyProperties properties;

    @Mock
    private GmailMessageMapper gmailMessageMapper;

    @Mock
    private GmailQueryBuilder gmailQueryBuilder;

    // Gmail API call chain: Gmail → Users → Labels → Create
    @Mock
    private Gmail gmail;

    @Mock
    private Gmail.Users users;

    @Mock
    private Gmail.Users.Labels labels;

    @Mock
    private Gmail.Users.Labels.Create labelsCreate;

    private GmailRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GmailRepositoryImpl(
                gmailClient, gmailBatchClient, tokenProvider, properties, gmailMessageMapper, gmailQueryBuilder);
    }

    // -------------------------------------------------------------------------
    // Helper: set up the standard Gmail labels.create mock chain
    // -------------------------------------------------------------------------

    private void givenGmailLabelCreateChainReturns(Label createdLabel) throws Exception {
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);
        when(labels.create(eq(TEST_USER_ID), any(Label.class))).thenReturn(labelsCreate);
        when(labelsCreate.execute()).thenReturn(createdLabel);
    }

    // -------------------------------------------------------------------------
    // Helper: build a GoogleJsonResponseException with a specific status code
    //
    // createLabel's 409 branch only inspects getStatusCode() before throwing
    // LabelAlreadyExistsException (getMessage() is only read on the non-409,
    // log-and-rethrow path), so only getStatusCode() is stubbed here — stubbing
    // getMessage()/getDetails() would trip Mockito's UnnecessaryStubbingException
    // under strict stubs for the 409 case this helper is used for.
    // -------------------------------------------------------------------------

    private GoogleJsonResponseException buildGoogleJsonException(int statusCode) {
        GoogleJsonResponseException exception = mock(GoogleJsonResponseException.class);
        when(exception.getStatusCode()).thenReturn(statusCode);
        return exception;
    }

    // -------------------------------------------------------------------------
    // Happy path — success returns LabelSummary with correct id/name/type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validName_returnsLabelSummaryWithCorrectIdNameAndType")
    void createLabel_validName_returnsLabelSummaryWithCorrectIdNameAndType() throws Exception {
        // Arrange
        Label createdLabel =
                new Label().setId(TEST_LABEL_ID).setName(TEST_LABEL_NAME).setType("user");
        givenGmailLabelCreateChainReturns(createdLabel);

        LabelSummary expectedSummary = new LabelSummary(TEST_LABEL_ID, TEST_LABEL_NAME, "user", null, null);
        when(gmailMessageMapper.toLabelSummary(createdLabel)).thenReturn(expectedSummary);

        // Act
        LabelSummary result = repository.createLabel(TEST_USER_ID, TEST_LABEL_NAME, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(TEST_LABEL_ID);
        assertThat(result.name()).isEqualTo(TEST_LABEL_NAME);
        assertThat(result.type()).isEqualTo("user");
        verify(gmailMessageMapper).toLabelSummary(createdLabel);
    }

    // -------------------------------------------------------------------------
    // Verify the correct 3-call Gmail API chain is invoked, and the submitted
    // Label payload carries the requested name (and no visibility fields when null)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_validNameNoVisibility_defaultsVisibilityFieldsOnCreatePayload")
    void createLabel_validNameNoVisibility_defaultsVisibilityFieldsOnCreatePayload() throws Exception {
        // Arrange
        Label createdLabel =
                new Label().setId(TEST_LABEL_ID).setName(TEST_LABEL_NAME).setType("user");

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);

        ArgumentCaptor<Label> labelCaptor = ArgumentCaptor.forClass(Label.class);
        when(labels.create(eq(TEST_USER_ID), labelCaptor.capture())).thenReturn(labelsCreate);
        when(labelsCreate.execute()).thenReturn(createdLabel);
        when(gmailMessageMapper.toLabelSummary(createdLabel))
                .thenReturn(new LabelSummary(TEST_LABEL_ID, TEST_LABEL_NAME, "user", null, null));

        // Act
        repository.createLabel(TEST_USER_ID, TEST_LABEL_NAME, null, null);

        // Assert: verify the full 3-call chain — gmail.users().labels().create(...).execute()
        verify(tokenProvider).getAccessToken();
        verify(gmailClient).createGmailService(TEST_ACCESS_TOKEN);
        verify(gmail).users();
        verify(users).labels();
        verify(labels).create(eq(TEST_USER_ID), any(Label.class));
        verify(labelsCreate).execute();

        // The generated Gmail client marks both visibility fields REQUIRED on labels.create, so a
        // name-only request MUST carry Gmail's defaults — regression guard for the live 500 that
        // client-side checkRequiredParameter threw when they were null.
        Label captured = labelCaptor.getValue();
        assertThat(captured.getName()).isEqualTo(TEST_LABEL_NAME);
        assertThat(captured.getMessageListVisibility()).isEqualTo("show");
        assertThat(captured.getLabelListVisibility()).isEqualTo("labelShow");
    }

    // -------------------------------------------------------------------------
    // Non-null visibility values are carried into the Gmail create request payload
    // (complements the null-visibility assertions in the test above)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_nonNullVisibilityValues_invokesLabelsCreateWithVisibilityFieldsSet")
    void createLabel_nonNullVisibilityValues_invokesLabelsCreateWithVisibilityFieldsSet() throws Exception {
        // Arrange
        String messageListVisibility = "show";
        String labelListVisibility = "labelShow";
        Label createdLabel = new Label()
                .setId(TEST_LABEL_ID)
                .setName(TEST_LABEL_NAME)
                .setType("user")
                .setMessageListVisibility(messageListVisibility)
                .setLabelListVisibility(labelListVisibility);

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);

        ArgumentCaptor<Label> labelCaptor = ArgumentCaptor.forClass(Label.class);
        when(labels.create(eq(TEST_USER_ID), labelCaptor.capture())).thenReturn(labelsCreate);
        when(labelsCreate.execute()).thenReturn(createdLabel);
        when(gmailMessageMapper.toLabelSummary(createdLabel))
                .thenReturn(new LabelSummary(
                        TEST_LABEL_ID, TEST_LABEL_NAME, "user", messageListVisibility, labelListVisibility));

        // Act
        repository.createLabel(TEST_USER_ID, TEST_LABEL_NAME, messageListVisibility, labelListVisibility);

        // Assert: non-null messageListVisibility/labelListVisibility values are set on
        // the Label payload sent to Gmail, not silently dropped.
        Label captured = labelCaptor.getValue();
        assertThat(captured.getName()).isEqualTo(TEST_LABEL_NAME);
        assertThat(captured.getMessageListVisibility()).isEqualTo(messageListVisibility);
        assertThat(captured.getLabelListVisibility()).isEqualTo(labelListVisibility);
    }

    // -------------------------------------------------------------------------
    // GoogleJsonResponseException 409 → LabelAlreadyExistsException, name-free message
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_duplicateNameConflict_throwsLabelAlreadyExistsExceptionWithoutLeakingName")
    void createLabel_duplicateNameConflict_throwsLabelAlreadyExistsExceptionWithoutLeakingName() throws Exception {
        // Arrange: Gmail rejects the create with a 409 conflict when the name already exists.
        GoogleJsonResponseException gmailError = buildGoogleJsonException(409);

        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.labels()).thenReturn(labels);
        when(labels.create(eq(TEST_USER_ID), any(Label.class))).thenReturn(labelsCreate);
        when(labelsCreate.execute()).thenThrow(gmailError);

        // Act & Assert: FR-015 — the message must never embed the requested label name.
        assertThatThrownBy(() -> repository.createLabel(TEST_USER_ID, TEST_LABEL_NAME, null, null))
                .isInstanceOf(LabelAlreadyExistsException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(TEST_LABEL_NAME));
    }

    // -------------------------------------------------------------------------
    // GeneralSecurityException from getGmailService() wraps to IOException
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createLabel_generalSecurityExceptionFromServiceCreation_wrapsToIOException")
    void createLabel_generalSecurityExceptionFromServiceCreation_wrapsToIOException() throws Exception {
        // Arrange: gmailClient.createGmailService throws GeneralSecurityException.
        when(tokenProvider.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
        when(gmailClient.createGmailService(TEST_ACCESS_TOKEN))
                .thenThrow(new GeneralSecurityException("Key store failure"));

        // Act & Assert
        assertThatThrownBy(() -> repository.createLabel(TEST_USER_ID, TEST_LABEL_NAME, null, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Security exception creating Gmail service");
    }
}
