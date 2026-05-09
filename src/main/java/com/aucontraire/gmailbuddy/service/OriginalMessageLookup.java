package com.aucontraire.gmailbuddy.service;

/**
 * Per-request value object holding the results of the {@code users.messages.get}
 * metadata call made when {@code inReplyToMessageId} is supplied by the caller.
 *
 * <p>Created by {@code GmailRepositoryImpl.getMessageHeaders(...)}, passed to
 * {@code MimeMessageBuilder.build(SendMessageDTO, OriginalMessageLookup)} and the
 * service's thread-ID resolution logic, and discarded once the {@code MimeMessage}
 * is built. This record is never persisted and is never returned to the API caller.</p>
 *
 * <p>No fields in this record are ever logged at any level. {@code rfcMessageId}
 * and {@code messageId} are internal references; {@code rfcMessageId} is used
 * only to set {@code In-Reply-To} and {@code References} headers on the outgoing
 * MIME message (FR-019).</p>
 */
public record OriginalMessageLookup(
        String messageId,
        String threadId,
        String rfcMessageId
) {}
