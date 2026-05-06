package com.aucontraire.gmailbuddy.service;

/** Immutable domain DTO returned by the repository after a draft is created, carrying the Gmail-assigned draft, message, and thread identifiers. */
public record DraftCreationResult(String draftId, String messageId, String threadId) {}
