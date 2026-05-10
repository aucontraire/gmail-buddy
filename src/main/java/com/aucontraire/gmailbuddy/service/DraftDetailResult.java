package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.response.AttachmentMetadata;

import java.util.List;

/**
 * Internal domain record holding the parsed contents of a single draft.
 *
 * <p>Returned by {@code GmailRepository.getDraft(...)} and used as the element
 * type inside {@link DraftListResult}. Created by {@code GmailRepositoryImpl}
 * (via {@code GmailMessageMapper.toDraftDetailResult}) after extracting fields
 * from the Gmail SDK {@code Draft} object. Keeps Gmail SDK types out of
 * service-layer signatures (Constitution II).</p>
 *
 * <p>List fields are never null — the mapper uses {@code List.of()} as fallback
 * for missing recipients/attachments. String fields may be null when the
 * underlying MIME header is absent (e.g., draft with no subject).</p>
 *
 * @see com.aucontraire.gmailbuddy.mapper.GmailMessageMapper#toDraftDetailResult
 */
public record DraftDetailResult(
        String draftId,
        String messageId,
        String threadId,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String snippet,
        String body,
        String bodyType,
        String inReplyToMessageId,
        List<AttachmentMetadata> attachments
) {}
