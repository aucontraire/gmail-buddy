package com.aucontraire.gmailbuddy.service;

import com.aucontraire.gmailbuddy.dto.response.ThreadSummary;

import java.util.List;

/**
 * Internal domain record returned by {@code GmailRepository.listThreads(...)}.
 * Holds the page of thread summaries and pagination state.
 *
 * <p>{@code ThreadSummary} is used directly here — for the list case no separate
 * domain record is needed between the repository boundary and the response because
 * the stub fields ({@code id}, {@code snippet}, {@code historyId}) are already
 * domain-clean (no Gmail SDK types). Follows the same reasoning as
 * {@code MessageListResult} using {@code MessageSummary} elements.</p>
 */
public record ThreadListResult(
        List<ThreadSummary> threads,
        String nextPageToken,
        Integer totalCount
) {}
