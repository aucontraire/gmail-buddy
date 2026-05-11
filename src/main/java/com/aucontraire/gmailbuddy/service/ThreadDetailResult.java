package com.aucontraire.gmailbuddy.service;

import java.util.List;

/**
 * Internal domain record holding the full thread content returned by
 * {@code GmailRepository.getThread(...)}. Keeps Gmail SDK {@code Thread} type
 * out of service-layer signatures (Constitution II).
 *
 * <p>{@code labelIds} is the union of label IDs from all {@code MessageDetailResult}
 * objects in {@code messages} — computed by the mapper when building this record
 * from the Gmail SDK {@code Thread} object. {@code messages} is in chronological
 * ascending order (oldest first) as returned by Gmail's API.</p>
 */
public record ThreadDetailResult(
        String threadId,
        List<String> labelIds,
        List<MessageDetailResult> messages
) {}
