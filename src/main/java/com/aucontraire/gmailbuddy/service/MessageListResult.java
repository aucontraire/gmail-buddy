package com.aucontraire.gmailbuddy.service;

import com.google.api.services.gmail.model.Message;
import java.util.List;

/**
 * Represents the result of a paginated message list operation.
 * Contains the list of messages, pagination token for the next page,
 * and an estimated total count of results.
 */
public class MessageListResult {
    private final List<Message> messages;
    private final String nextPageToken;
    private final Integer resultSizeEstimate;

    public MessageListResult(List<Message> messages, String nextPageToken, Integer resultSizeEstimate) {
        this.messages = messages;
        this.nextPageToken = nextPageToken;
        this.resultSizeEstimate = resultSizeEstimate;
    }

    /**
     * Returns the list of messages for the current page.
     * @return list of messages, may be null or empty if no messages found
     */
    public List<Message> getMessages() {
        return messages;
    }

    /**
     * Returns the token for fetching the next page of results.
     * @return next page token, or null if there are no more pages
     */
    public String getNextPageToken() {
        return nextPageToken;
    }

    /**
     * Returns the estimated total number of results matching the query.
     * This is an estimate and may not be exact.
     * @return estimated result count
     */
    public Integer getResultSizeEstimate() {
        return resultSizeEstimate;
    }

    /**
     * Checks if there are more pages of results available.
     * @return true if there is a next page token, false otherwise
     */
    public boolean hasMore() {
        return nextPageToken != null && !nextPageToken.isEmpty();
    }
}
