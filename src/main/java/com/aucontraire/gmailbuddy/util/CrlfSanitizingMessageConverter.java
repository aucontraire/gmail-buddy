package com.aucontraire.gmailbuddy.util;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback MessageConverter that escapes CR and LF characters in every formatted
 * log message before it is written to any appender.
 *
 * <p>Purpose (FR-024): Any user-supplied string that reaches log output — including
 * recipient email addresses, subject lines, rejected-value fields from validation
 * failures, and Gmail query strings derived from request fields — may contain
 * carriage-return ({@code \r}, U+000D) or line-feed ({@code \n}, U+000A) characters.
 * Leaving these characters unsanitized allows an attacker to inject synthetic log
 * lines by crafting a value such as {@code victim@example.com\nINFO 2026-01-01 FAKE
 * LOG LINE}. The converter replaces {@code \r} with the literal four-character
 * sequence {@code \\r} and {@code \n} with {@code \\n}, making injected newlines
 * visible as data rather than as log-line boundaries.
 *
 * <p>Registration: This converter is registered in {@code logback.xml} via the
 * {@code <conversionRule>} element using the conversion word {@code sanitizedMsg},
 * which replaces the standard {@code %msg} specifier in the encoder pattern.
 * Because sanitization is applied inside the pattern rather than at individual
 * log-call sites, it cannot be bypassed by a {@code log.info(...)} call that
 * omits a manual sanitization step.
 *
 * <p>Constitution-VII deviation context: Recipient email addresses and subject
 * lines are PII under a strict reading of Constitution Principle VII. They are
 * logged under a controlled deviation justified in plan.md § Complexity Tracking
 * (single-user, local-machine deployment; operator is the data controller).
 * This converter mitigates one specific risk of that deviation — log-line injection
 * — but does not lift the underlying PII-in-logs concern. Revisit if external log
 * shipping or multi-user mode is introduced.
 *
 * @see <a href="../../../../../../../specs/001-send-draft-emails/spec.md">FR-024</a>
 */
public class CrlfSanitizingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null) {
            return null;
        }
        return message
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
