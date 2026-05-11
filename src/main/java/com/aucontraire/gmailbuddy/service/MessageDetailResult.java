package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;

import java.util.List;
import java.util.Map;

/**
 * Internal domain record holding the parsed contents of a single message.
 *
 * <p>Returned by {@code GmailRepository.getMessageDetail(...)} and used as the
 * element type inside {@link ThreadDetailResult#messages}. Keeps Gmail SDK
 * {@code Message} types out of service-layer signatures (Constitution II).</p>
 *
 * <p>{@code headers} is the post-whitelist filtered map (max 9 keys per
 * Clarifications Q2). {@code body} is null when the repository was called
 * with {@code format=metadata}. {@code attachments} is {@code List.of()} for
 * messages with no attachment parts (never null).</p>
 *
 * <p>Mirrors the field shape of
 * {@link com.aucontraire.gmailbuddy.dto.response.MessageDetailResponse} — the
 * mapper does the cross-projection.</p>
 *
 * @see com.aucontraire.gmailbuddy.mapper.GmailMessageMapper#toMessageDetailResult
 */
public record MessageDetailResult(
        String id,
        String threadId,
        Map<String, String> headers,
        String snippet,
        String body,
        String bodyType,
        List<String> labelIds,
        List<MessageAttachmentMetadata> attachments
) {}
