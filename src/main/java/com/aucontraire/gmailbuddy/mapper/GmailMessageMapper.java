package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.service.DraftCreationResult;
import com.aucontraire.gmailbuddy.service.SentMessageResult;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import org.springframework.stereotype.Component;

/**
 * Boundary mapper that converts Gmail SDK types into project-internal domain
 * DTOs at the repository layer, keeping Gmail SDK types out of higher-level
 * layer signatures.
 *
 * <p>Per Constitution Principle II and research.md Decision 13, repository
 * interfaces MUST NOT expose Gmail API types. This mapper is the seam that
 * converts {@link com.google.api.services.gmail.model.Message} and
 * {@link com.google.api.services.gmail.model.Draft} to the corresponding
 * project records before the data crosses the repository boundary.</p>
 *
 * <p>The service layer and controller layer are therefore free of Gmail SDK
 * imports for the new send/draft code path.</p>
 *
 * @see SentMessageResult
 * @see DraftCreationResult
 */
@Component
public class GmailMessageMapper {

    /**
     * Converts a Gmail API {@link Message} (as returned by
     * {@code users.messages.send} or {@code users.drafts.send}) into a
     * {@link SentMessageResult} domain record.
     *
     * @param message the Gmail API message response; must not be {@code null}
     * @return a {@link SentMessageResult} carrying the assigned message and
     *         thread identifiers
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public SentMessageResult toSentMessageResult(Message message) {
        return new SentMessageResult(message.getId(), message.getThreadId());
    }

    /**
     * Converts a Gmail API {@link Draft} (as returned by
     * {@code users.drafts.create}) into a {@link DraftCreationResult} domain
     * record.
     *
     * <p>The {@code messageId} and {@code threadId} are extracted from the
     * nested {@code draft.getMessage()} object that Gmail populates in the
     * create response. If {@code draft.getMessage()} is {@code null} (should
     * not occur for a successful create response), both identifiers are set to
     * {@code null}.</p>
     *
     * @param draft the Gmail API draft response; must not be {@code null}
     * @return a {@link DraftCreationResult} carrying the assigned draft, message,
     *         and thread identifiers
     * @throws NullPointerException if {@code draft} is {@code null}
     */
    public DraftCreationResult toDraftCreationResult(Draft draft) {
        Message nestedMessage = draft.getMessage();
        String messageId = nestedMessage != null ? nestedMessage.getId() : null;
        String threadId  = nestedMessage != null ? nestedMessage.getThreadId() : null;
        return new DraftCreationResult(draft.getId(), messageId, threadId);
    }
}
