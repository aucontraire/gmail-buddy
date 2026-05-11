package com.aucontraire.gmailbuddy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Full message content returned by {@code GET /api/v1/gmail/messages/{id}}
 * and used as the per-message element inside
 * {@link ThreadDetailResponse#messages} (same record type in both contexts).
 *
 * <p>The {@code headers} map is whitelisted to exactly the 9 canonical RFC
 * 5322 names per Clarifications Q2: {@code From}, {@code To}, {@code Cc},
 * {@code Bcc}, {@code Subject}, {@code Date}, {@code In-Reply-To},
 * {@code Message-ID}, {@code References}. Absent headers produce no map entry;
 * the map is empty (not null) when none of the 9 are present. Keys use this
 * exact casing.</p>
 *
 * <p>{@code body} and {@code bodyType} are null when the request was made
 * with {@code ?format=metadata}; both are populated for {@code ?format=full}
 * (the default). See data-model §6.</p>
 *
 * @param id         Gmail message identifier
 * @param threadId   Gmail thread identifier; null if not threaded
 * @param headers    Whitelisted RFC 5322 headers (max 9 keys); empty if none present
 * @param snippet    Gmail-provided body preview (~100 chars); from {@code Message.snippet}
 * @param body       Decoded body text; null when {@code ?format=metadata}
 * @param bodyType   {@code "html"} or {@code "text"}; null when {@code ?format=metadata}
 * @param labelIds   Per-message label IDs; empty list if none
 * @param attachments Attachment metadata list (no binary content); empty list if none
 */
@Schema(description = "Full message content with whitelisted headers and attachment metadata")
public record MessageDetailResponse(
        @Schema(description = "Gmail message identifier", example = "1976a4bc3fe89d0c")
        String id,

        @Schema(description = "Gmail thread identifier; null if not threaded",
                example = "1976a4bc3fe89d0c")
        String threadId,

        @Schema(description = "Whitelisted RFC 5322 headers; absent headers produce no map entry",
                example = "{\"From\":\"recruiter@example.com\",\"Subject\":\"Hello\"}")
        Map<String, String> headers,

        @Schema(description = "Gmail-provided body preview (~100 chars)",
                example = "Hi there, I wanted to follow up...")
        String snippet,

        @Schema(description = "Decoded body text; null when ?format=metadata")
        String body,

        @Schema(description = "Body content type", example = "html",
                allowableValues = {"html", "text"})
        String bodyType,

        @Schema(description = "Per-message label IDs", example = "[\"INBOX\", \"UNREAD\"]")
        List<String> labelIds,

        @Schema(description = "Attachment metadata list (no binary content)")
        List<MessageAttachmentMetadata> attachments
) {}
