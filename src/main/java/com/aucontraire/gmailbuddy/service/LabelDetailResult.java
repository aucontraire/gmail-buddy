package com.aucontraire.gmailbuddy.service;

/**
 * Internal domain record holding the full detail of a single Gmail label.
 *
 * <p>Returned by {@code GmailRepository.getLabel(...)} and consumed by
 * {@code GmailService.getLabel(...)} then projected to
 * {@code LabelDetailResponse} by the mapper. Keeps the Gmail SDK
 * {@code Label} type out of service-layer signatures (Constitution II).</p>
 *
 * <p>Color is stored as two flat {@code String} fields (rather than a nested
 * record) so that the domain record remains a pure data holder without any
 * dependency on DTO types. The mapper converts these to a {@code LabelColor}
 * record when projecting to {@code LabelDetailResponse} per data-model §19.</p>
 *
 * @param id                    Gmail label ID (e.g., {@code INBOX}, {@code Label_42})
 * @param name                  Display name (e.g., {@code INBOX}, {@code Recruiters})
 * @param type                  {@code "system"} or {@code "user"} from {@code Label.getType()}
 * @param messageListVisibility Gmail's messageListVisibility; null if not set
 * @param labelListVisibility   Gmail's labelListVisibility; null if not set
 * @param colorTextColor        Text color hex string; null if no color configured
 * @param colorBackgroundColor  Background color hex string; null if no color configured
 * @param messagesTotal         Total messages with this label; null if not populated by Gmail
 * @param messagesUnread        Unread messages with this label; null if not populated
 * @param threadsTotal          Total threads with this label; null if not populated
 * @param threadsUnread         Unread threads with this label; null if not populated
 */
public record LabelDetailResult(
        String id,
        String name,
        String type,
        String messageListVisibility,
        String labelListVisibility,
        String colorTextColor,
        String colorBackgroundColor,
        Integer messagesTotal,
        Integer messagesUnread,
        Integer threadsTotal,
        Integer threadsUnread
) {}
