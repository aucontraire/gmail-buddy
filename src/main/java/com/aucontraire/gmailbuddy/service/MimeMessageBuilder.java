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
     * <p>The returned {@code MimeMessage} is created on a minimal no-op JavaMail
     * {@link Session} (no SMTP configuration) because delivery goes through the
     * Gmail HTTP API, not SMTP.</p>
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

        // Materialize all staged changes into the actual MIME header set so that
        // getContentType(), getRecipients(), and writeTo() all reflect the intended
        // values without requiring the caller to invoke saveChanges() themselves.
        message.saveChanges();

        return message;
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
