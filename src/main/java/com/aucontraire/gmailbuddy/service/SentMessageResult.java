package com.aucontraire.gmailbuddy.service;

/** Immutable domain DTO returned by the repository after a message is sent, carrying the Gmail-assigned message and thread identifiers. */
public record SentMessageResult(String messageId, String threadId) {}
