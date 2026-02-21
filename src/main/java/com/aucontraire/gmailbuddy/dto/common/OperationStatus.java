package com.aucontraire.gmailbuddy.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum representing the status of an API operation.
 * Provides semantic clarity over boolean flags like "completeSuccess".
 *
 * @since 1.0
 */
@Schema(description = "Status of an API operation", enumAsRef = true)
public enum OperationStatus {
    /**
     * Operation completed successfully - all items processed without errors
     */
    SUCCESS("Operation completed successfully"),

    /**
     * Operation partially completed - some items succeeded, some failed
     */
    PARTIAL_SUCCESS("Operation partially completed"),

    /**
     * No items matched the criteria - operation didn't fail, just found nothing
     */
    NO_RESULTS("No items matched the criteria"),

    /**
     * Operation failed - all items failed to process
     */
    FAILURE("Operation failed");

    private final String description;

    OperationStatus(String description) {
        this.description = description;
    }

    /**
     * Get human-readable description of the status
     * @return status description
     */
    public String getDescription() {
        return description;
    }
}