package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.SendMessageDTO;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

// Note on responsibilities (T034):
// MimeMessageBuilder is responsible for constructing the RFC 5322 MIME message,
// including setting In-Reply-To and References headers when a threading lookup
// is provided. It does NOT set threadId on the Gmail API Message object —
// that is the caller's (GmailService's) responsibility and happens after build()
// returns, using the value from resolveThreadId(dto, lookup).

/**
 * Converts a {@link SendMessageDTO} into a fully-constructed
 * {@link MimeMessage} ready for base64url encoding and submission to the Gmail
 * API via {@code users.messages.send} or {@code users.drafts.create}.
 *
 * <p>This builder is a pure conversion utility. It performs no validation
 * beyond what JavaMail itself enforces during address parsing (validation is the
 * responsibility of the Bean Validation layer on {@code SendMessageDTO}).</p>
 *
 * <p>HTML bodies are forwarded verbatim without server-side sanitization per
 * research.md Decision 7. The recipient's email client is the trust boundary
 * for render safety.</p>
 *
 * <h2>Content-type mapping</h2>
 * <ul>
 *   <li>{@code bodyType = "text"} → MIME type {@code text/plain; charset=UTF-8}</li>
 *   <li>{@code bodyType = "html"} → MIME type {@code text/html; charset=UTF-8}</li>
 * </ul>
 *
 * <h2>Recipient handling</h2>
 * <p>Each address in {@code to}, {@code cc}, and {@code bcc} is parsed via
 * {@link InternetAddress#parse(String)} and set on the appropriate
 * {@link RecipientType}. Empty or null lists produce no recipient headers for
 * that field.</p>
 *
 * @see SendMessageDTO
 * @see MimeMessage
 */
@Component
public class MimeMessageBuilder {

    private static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=UTF-8";
    private static final String CONTENT_TYPE_TEXT_HTML  = "text/html; charset=UTF-8";

    /**
     * Builds a {@link MimeMessage} from the validated {@link SendMessageDTO}.
     *
     * <p>Delegates to {@link #build(SendMessageDTO, OriginalMessageLookup)} with a
     * {@code null} lookup, preserving backward compatibility: no {@code In-Reply-To}
     * or {@code References} headers are added, and no thread-ID resolution is
     * performed.</p>
     *
     * @param dto the validated send request DTO; must not be {@code null}
     * @return a fully-populated {@code MimeMessage}
     * @throws MessagingException            if JavaMail encounters an error while
     *                                       constructing or setting headers/content
     * @throws UnsupportedEncodingException  if the UTF-8 charset is unavailable
     *                                       (should never occur on any conforming JVM)
     */
    public MimeMessage build(SendMessageDTO dto)
            throws MessagingException, UnsupportedEncodingException {
        return build(dto, null);
    }

    /**
     * Builds a {@link MimeMessage} from the validated {@link SendMessageDTO}, optionally
     * adding RFC 5322 threading headers ({@code In-Reply-To} and {@code References})
     * when a non-null {@link OriginalMessageLookup} is supplied.
     *
     * <p>The returned {@code MimeMessage} is created on a minimal no-op JavaMail
     * {@link Session} (no SMTP configuration) because delivery goes through the
     * Gmail HTTP API, not SMTP.</p>
     *
     * <h2>Threading header placement</h2>
     * <p>When {@code lookup} is non-null, {@code In-Reply-To} and {@code References}
     * are set on the {@code MimeMessage} AFTER body/content construction and BEFORE
     * {@code saveChanges()}, using {@code lookup.rfcMessageId()} as the value. The
     * {@code rfcMessageId} is already angle-bracket-delimited per RFC 5322
     * (e.g., {@code <CABc123xyz@mail.gmail.com>}) and is used as-is.</p>
     *
     * <h2>Responsibility split (research.md Decision 2)</h2>
     * <p>This method handles ONLY the MIME-level threading headers ({@code In-Reply-To},
     * {@code References}). The Gmail API {@code Message.setThreadId(...)} call is the
     * caller's responsibility (performed in {@code GmailService} after this method
     * returns) using the value from {@link #resolveThreadId(SendMessageDTO, OriginalMessageLookup)}.
     * The two concerns are intentionally separated: MIME headers live in the RFC 5322
     * payload; {@code threadId} is a Gmail API envelope field.</p>
     *
     * @param dto    the validated send request DTO; must not be {@code null}
     * @param lookup the result of the {@code users.messages.get} lookup for the original
     *               message; {@code null} when no threading is requested (FR-007 pass-through
     *               or non-threaded send)
     * @return a fully-populated {@code MimeMessage}, with {@code In-Reply-To} and
     *         {@code References} headers set when {@code lookup} is non-null
     * @throws MessagingException            if JavaMail encounters an error while
     *                                       constructing or setting headers/content
     * @throws UnsupportedEncodingException  if the UTF-8 charset is unavailable
     *                                       (should never occur on any conforming JVM)
     */
    public MimeMessage build(SendMessageDTO dto, OriginalMessageLookup lookup)
            throws MessagingException, UnsupportedEncodingException {

        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);

        setRecipients(message, RecipientType.TO, dto.to());
        setRecipients(message, RecipientType.CC, dto.cc());
        setRecipients(message, RecipientType.BCC, dto.bcc());

        message.setSubject(dto.subject(), StandardCharsets.UTF_8.name());

        String contentType = "html".equalsIgnoreCase(dto.bodyType())
                ? CONTENT_TYPE_TEXT_HTML
                : CONTENT_TYPE_TEXT_PLAIN;
        message.setContent(dto.body(), contentType);

        // Set RFC 5322 threading headers AFTER body construction and BEFORE saveChanges().
        // lookup.rfcMessageId() already contains the angle-bracket-delimited value
        // (e.g., <CABc123xyz@mail.gmail.com>) per RFC 5322 §3.6.4 — use as-is.
        if (lookup != null) {
            message.addHeader("In-Reply-To", lookup.rfcMessageId());
            message.addHeader("References", lookup.rfcMessageId());
        }

        // Materialize all staged changes into the actual MIME header set so that
        // getContentType(), getRecipients(), and writeTo() all reflect the intended
        // values without requiring the caller to invoke saveChanges() themselves.
        message.saveChanges();

        return message;
    }

    /**
     * Resolves the Gmail thread ID to set on the outgoing {@code Message} API object.
     *
     * <p>Priority rules (FR-005, FR-006, FR-007):</p>
     * <ol>
     *   <li>If {@code lookup} is non-null, return {@code lookup.threadId()} — the
     *       thread ID from the fetched original message always wins over any caller-supplied
     *       value (FR-006 defensive rule: trust Gmail's reported value over client data).</li>
     *   <li>If {@code lookup} is null but {@code dto.threadId()} is non-null, return
     *       {@code dto.threadId()} — pass-through for the threadId-only case (FR-007).</li>
     *   <li>Otherwise, return {@code null} — no thread ID is set; the message starts a
     *       new thread (standard v0.2.0 behavior).</li>
     * </ol>
     *
     * <p>This method is package-private so {@code GmailService} in the same package can
     * call it directly after {@link #build(SendMessageDTO, OriginalMessageLookup)} returns,
     * keeping the {@code Message.setThreadId(...)} call in the service layer where the
     * Gmail API {@code Message} object is managed.</p>
     *
     * @param dto    the send request DTO
     * @param lookup the original-message lookup result; {@code null} when no lookup was
     *               performed (i.e., {@code dto.inReplyToMessageId()} was absent)
     * @return the resolved thread ID to pass to {@code Message.setThreadId(...)}, or
     *         {@code null} if no thread should be specified
     */
    String resolveThreadId(SendMessageDTO dto, OriginalMessageLookup lookup) {
        if (lookup != null) {
            // FR-006: lookup's threadId wins unconditionally — Gmail's reported value
            // is authoritative even if the caller supplied a different threadId.
            return lookup.threadId();
        }
        // FR-007: no lookup performed; pass caller's threadId through unchanged (may be null).
        return dto.threadId();
    }

    /**
     * Parses the address strings in {@code addresses} and sets them on
     * {@code message} for the given {@code recipientType}. Does nothing when
     * the list is {@code null} or empty.
     *
     * @param message       the message to modify
     * @param recipientType {@code TO}, {@code CC}, or {@code BCC}
     * @param addresses     the list of RFC 5322 address strings
     * @throws MessagingException if any address cannot be set
     */
    private void setRecipients(MimeMessage message,
                                RecipientType recipientType,
                                List<String> addresses)
            throws MessagingException {

        if (addresses == null || addresses.isEmpty()) {
            return;
        }

        InternetAddress[] parsed = new InternetAddress[addresses.size()];
        for (int i = 0; i < addresses.size(); i++) {
            // InternetAddress.parse is lenient; validation already done by DTO constraints.
            InternetAddress[] singleParsed = InternetAddress.parse(addresses.get(i), false);
            parsed[i] = singleParsed[0];
        }
        message.setRecipients(recipientType, parsed);
    }
}
