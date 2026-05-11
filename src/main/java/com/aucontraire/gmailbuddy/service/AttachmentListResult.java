package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.response.MessageAttachmentMetadata;

import java.util.List;

/**
 * Per-request value object returned by {@code GmailRepository.listAttachments(...)}.
 * Holds the list of attachment metadata items for a single message.
 *
 * <p>Per Constitution III: lists are never null — {@code List.of()} is used
 * when the message has no attachments.</p>
 *
 * @param results list of attachment metadata items; never null; empty when the
 *                message has no attachments
 */
public record AttachmentListResult(
        List<MessageAttachmentMetadata> attachments
) {}
