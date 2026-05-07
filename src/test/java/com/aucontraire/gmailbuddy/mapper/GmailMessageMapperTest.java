package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link GmailMessageMapper}.
 *
 * <p>The mapper is a pure conversion utility with no external dependencies.
 * Gmail SDK types ({@link Message}, {@link Draft}) are SDK POJOs — they need
 * no mocking; instances are constructed directly using their setter API and
 * asserted against the resulting domain records.</p>
 *
 * <p>Test naming follows the project convention:
 * {@code methodName_stateUnderTest_expectedBehavior}.</p>
 */
class GmailMessageMapperTest {

    private GmailMessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GmailMessageMapper();
    }

    // -------------------------------------------------------------------------
    // toSentMessageResult — happy path
    // -------------------------------------------------------------------------

    @Test
    void toSentMessageResult_messageWithIdAndThreadId_returnsResultWithBothFields() {
        // Arrange
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-abc-123");
        gmailMessage.setThreadId("thread-xyz-456");

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("msg-abc-123");
        assertThat(result.threadId()).isEqualTo("thread-xyz-456");
    }

    @Test
    void toSentMessageResult_messageWithDifferentIds_resultReflectsCorrectValues() {
        // Arrange: verify no field swap — messageId maps to getId(), threadId to getThreadId().
        Message gmailMessage = new Message();
        gmailMessage.setId("message-id-sentinel");
        gmailMessage.setThreadId("thread-id-sentinel");

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("message-id-sentinel");
        assertThat(result.threadId()).isEqualTo("thread-id-sentinel");
    }

    @Test
    void toSentMessageResult_messageWithNullThreadId_resultHasNullThreadId() {
        // Arrange: Gmail may omit threadId in some edge-case responses;
        // the mapper must not add a null-guard that replaces it with a default.
        Message gmailMessage = new Message();
        gmailMessage.setId("msg-no-thread");
        // threadId intentionally not set — defaults to null

        // Act
        SentMessageResult result = mapper.toSentMessageResult(gmailMessage);

        // Assert
        assertThat(result.messageId()).isEqualTo("msg-no-thread");
        assertThat(result.threadId()).isNull();
    }

    @Test
    void toSentMessageResult_nullMessage_throwsNullPointerException() {
        // Arrange: the Javadoc contracts that null is disallowed;
        // passing null must propagate as NullPointerException (per @throws).

        // Act + Assert
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toSentMessageResult(null));
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — happy path
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_draftWithAllFields_returnsResultWithAllThreeFields() {
        // Arrange
        Message nestedMessage = new Message();
        nestedMessage.setId("msg-nested-789");
        nestedMessage.setThreadId("thread-nested-012");

        Draft draft = new Draft();
        draft.setId("draft-abc-001");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-abc-001");
        assertThat(result.messageId()).isEqualTo("msg-nested-789");
        assertThat(result.threadId()).isEqualTo("thread-nested-012");
    }

    @Test
    void toDraftCreationResult_draftWithDifferentIds_resultReflectsCorrectValues() {
        // Arrange: verify no field swap between draftId, messageId, threadId.
        Message nestedMessage = new Message();
        nestedMessage.setId("message-id-sentinel");
        nestedMessage.setThreadId("thread-id-sentinel");

        Draft draft = new Draft();
        draft.setId("draft-id-sentinel");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-id-sentinel");
        assertThat(result.messageId()).isEqualTo("message-id-sentinel");
        assertThat(result.threadId()).isEqualTo("thread-id-sentinel");
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — null nested Message (null-safety)
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_messageIdIsNull() {
        // Arrange: per the Javadoc, a null nested Message results in null messageId
        // and threadId. This covers the defensive null-safety branch in the mapper.
        Draft draft = new Draft();
        draft.setId("draft-no-message");
        // draft.setMessage() not called — nested Message is null

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-no-message");
        assertThat(result.messageId()).isNull();
    }

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_threadIdIsNull() {
        // Arrange
        Draft draft = new Draft();
        draft.setId("draft-no-message");
        // draft.setMessage() not called — nested Message is null

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.threadId()).isNull();
    }

    @Test
    void toDraftCreationResult_draftWithNullNestedMessage_draftIdStillPopulated() {
        // Arrange: even with a null nested message, the draft's own ID must
        // be present in the result — the null guard must not wipe the draftId.
        Draft draft = new Draft();
        draft.setId("draft-with-null-message");

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-with-null-message");
    }

    @Test
    void toDraftCreationResult_nullDraft_throwsNullPointerException() {
        // Arrange: the Javadoc contracts that null draft is disallowed.

        // Act + Assert
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toDraftCreationResult(null));
    }

    // -------------------------------------------------------------------------
    // toDraftCreationResult — nested Message with null threadId
    // -------------------------------------------------------------------------

    @Test
    void toDraftCreationResult_nestedMessageWithNullThreadId_resultHasNullThreadId() {
        // Arrange: Gmail may omit threadId on the nested message in some responses.
        Message nestedMessage = new Message();
        nestedMessage.setId("msg-no-thread");
        // threadId intentionally not set

        Draft draft = new Draft();
        draft.setId("draft-msg-no-thread");
        draft.setMessage(nestedMessage);

        // Act
        DraftCreationResult result = mapper.toDraftCreationResult(draft);

        // Assert
        assertThat(result.draftId()).isEqualTo("draft-msg-no-thread");
        assertThat(result.messageId()).isEqualTo("msg-no-thread");
        assertThat(result.threadId()).isNull();
    }
}
