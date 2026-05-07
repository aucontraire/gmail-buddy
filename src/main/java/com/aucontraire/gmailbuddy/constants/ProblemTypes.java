package com.aucontraire.gmailbuddy.constants;

/**
 * Problem type URIs for RFC 7807 compliant error responses.
 *
 * These constants define the problem type URIs used in ProblemDetail responses.
 * Each URI uniquely identifies a specific problem type that can occur in the
 * Gmail Buddy application.
 *
 * The URIs use relative paths for flexibility and follow the pattern:
 * /problems/{problem-category}
 *
 * RFC 7807 specifies that the type URI should identify the problem type and
 * should ideally resolve to human-readable documentation about the problem.
 *
 * @author Gmail Buddy Team
 * @since 1.0
 * @see com.aucontraire.gmailbuddy.dto.error.ProblemDetail
 */
public final class ProblemTypes {

    /**
     * Base URI for problem types.
     * Can be configured to point to documentation server.
     */
    private static final String BASE_URI = "/problems";

    // Client Error Problem Types (4xx)

    /**
     * Validation error - input data failed validation rules.
     * HTTP Status: 400 Bad Request
     * Example: Missing required fields, invalid email format, etc.
     */
    public static final String VALIDATION_ERROR = BASE_URI + "/validation-error";

    /**
     * Message not found - the requested Gmail message does not exist.
     * HTTP Status: 404 Not Found
     * Example: Message ID is invalid or message has been deleted.
     */
    public static final String MESSAGE_NOT_FOUND = BASE_URI + "/message-not-found";

    /**
     * Resource not found - the requested resource does not exist.
     * HTTP Status: 404 Not Found
     * Example: Invalid endpoint, missing resource.
     */
    public static final String RESOURCE_NOT_FOUND = BASE_URI + "/resource-not-found";

    /**
     * Authentication failed - user authentication failed or token is invalid.
     * HTTP Status: 401 Unauthorized
     * Example: OAuth2 token expired, invalid credentials.
     */
    public static final String AUTHENTICATION_FAILED = BASE_URI + "/authentication-failed";

    /**
     * Authorization failed - user lacks permission to perform the action.
     * HTTP Status: 403 Forbidden
     * Example: User doesn't have access to the requested Gmail account.
     */
    public static final String AUTHORIZATION_FAILED = BASE_URI + "/authorization-failed";

    /**
     * Rate limit exceeded - too many requests in a given time window.
     * HTTP Status: 429 Too Many Requests
     * Example: Exceeded Gmail API quota, application rate limit exceeded.
     */
    public static final String RATE_LIMIT_EXCEEDED = BASE_URI + "/rate-limit-exceeded";

    /**
     * Constraint violation - request violates a business rule or constraint.
     * HTTP Status: 400 Bad Request
     * Example: Invalid parameter values, constraint validation failures.
     */
    public static final String CONSTRAINT_VIOLATION = BASE_URI + "/constraint-violation";

    // Send & Draft endpoints (feature 001)

    /**
     * Header injection detected - a header-bound field contains carriage-return or
     * line-feed characters, indicating a header-injection attempt.
     * HTTP Status: 400 Bad Request
     * Distinct from /problems/validation-error so security-relevant attempts can be
     * triaged separately from ordinary input validation failures (FR-015, FR-018).
     * Example: Subject or recipient address contains \r or \n.
     */
    public static final String HEADER_INJECTION_DETECTED = BASE_URI + "/header-injection-detected";

    /**
     * Invalid recipient - Gmail rejected one or more recipient addresses as invalid.
     * HTTP Status: 422 Unprocessable Entity
     * Triggered when Gmail returns 400 invalidArgument for a recipient address that
     * passed local validation but was rejected by the upstream mail provider (FR-018).
     * Example: Address is syntactically plausible but the mailbox does not exist.
     */
    public static final String INVALID_RECIPIENT = BASE_URI + "/invalid-recipient";

    /**
     * Daily send limit exceeded - the authenticated user has reached their Gmail
     * daily sending limit (Google returns 403 dailySendLimitExceeded).
     * HTTP Status: 429 Too Many Requests
     * Response includes Retry-After: 86400 (24-hour window reset).
     * Must NOT be routed through any application-level retry-with-backoff path (FR-019).
     * Example: User has exhausted their daily outbound message quota.
     */
    public static final String DAILY_SEND_LIMIT_EXCEEDED = BASE_URI + "/daily-send-limit-exceeded";

    // Server Error Problem Types (5xx)

    /**
     * Gmail API error - error occurred while communicating with Gmail API.
     * HTTP Status: 502 Bad Gateway or 503 Service Unavailable
     * Example: Gmail API is down, network timeout, API error response.
     */
    public static final String GMAIL_API_ERROR = BASE_URI + "/gmail-api-error";

    // Send & Draft endpoints (feature 001)

    /**
     * Message send failed - Gmail rejected or failed to process a send/draft-send
     * request for a reason not covered by a more specific problem type.
     * HTTP Status: 502 Bad Gateway
     * Catch-all for MessageSendException cases that do not map to INVALID_RECIPIENT
     * or DAILY_SEND_LIMIT_EXCEEDED (FR-018).
     * Example: Transient Gmail API failure, unexpected error response from provider.
     */
    public static final String MESSAGE_SEND_FAILED = BASE_URI + "/message-send-failed";

    /**
     * Service unavailable - the Gmail Buddy service is temporarily unavailable.
     * HTTP Status: 503 Service Unavailable
     * Example: Maintenance mode, circuit breaker open, dependency unavailable.
     */
    public static final String SERVICE_UNAVAILABLE = BASE_URI + "/service-unavailable";

    /**
     * Internal server error - unexpected error occurred on the server.
     * HTTP Status: 500 Internal Server Error
     * Example: Unhandled exceptions, programming errors.
     */
    public static final String INTERNAL_ERROR = BASE_URI + "/internal-error";

    /**
     * Quota exceeded - Gmail API quota has been exhausted.
     * HTTP Status: 429 Too Many Requests or 503 Service Unavailable
     * Example: Daily Gmail API quota exceeded.
     */
    public static final String QUOTA_EXCEEDED = BASE_URI + "/quota-exceeded";

    /**
     * Batch operation error - error occurred during batch operation processing.
     * HTTP Status: 207 Multi-Status or 500 Internal Server Error
     * Example: Some messages in a batch operation failed.
     */
    public static final String BATCH_OPERATION_ERROR = BASE_URI + "/batch-operation-error";

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static constants.
     */
    private ProblemTypes() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    /**
     * Checks if a problem type represents a client error (4xx).
     *
     * @param problemType the problem type URI to check
     * @return true if the problem type is a client error, false otherwise
     */
    public static boolean isClientError(String problemType) {
        return VALIDATION_ERROR.equals(problemType) ||
               MESSAGE_NOT_FOUND.equals(problemType) ||
               RESOURCE_NOT_FOUND.equals(problemType) ||
               AUTHENTICATION_FAILED.equals(problemType) ||
               AUTHORIZATION_FAILED.equals(problemType) ||
               RATE_LIMIT_EXCEEDED.equals(problemType) ||
               CONSTRAINT_VIOLATION.equals(problemType);
    }

    /**
     * Checks if a problem type represents a server error (5xx).
     *
     * @param problemType the problem type URI to check
     * @return true if the problem type is a server error, false otherwise
     */
    public static boolean isServerError(String problemType) {
        return GMAIL_API_ERROR.equals(problemType) ||
               SERVICE_UNAVAILABLE.equals(problemType) ||
               INTERNAL_ERROR.equals(problemType) ||
               QUOTA_EXCEEDED.equals(problemType) ||
               BATCH_OPERATION_ERROR.equals(problemType);
    }

    /**
     * Gets a human-readable description for a problem type.
     *
     * @param problemType the problem type URI
     * @return a description of the problem type, or null if not found
     */
    public static String getDescription(String problemType) {
        return switch (problemType) {
            case VALIDATION_ERROR -> "Input data failed validation rules";
            case MESSAGE_NOT_FOUND -> "The requested Gmail message does not exist";
            case RESOURCE_NOT_FOUND -> "The requested resource does not exist";
            case AUTHENTICATION_FAILED -> "User authentication failed or token is invalid";
            case AUTHORIZATION_FAILED -> "User lacks permission to perform this action";
            case RATE_LIMIT_EXCEEDED -> "Too many requests in a given time window";
            case CONSTRAINT_VIOLATION -> "Request violates a business rule or constraint";
            case GMAIL_API_ERROR -> "Error occurred while communicating with Gmail API";
            case SERVICE_UNAVAILABLE -> "The Gmail Buddy service is temporarily unavailable";
            case INTERNAL_ERROR -> "Unexpected error occurred on the server";
            case QUOTA_EXCEEDED -> "Gmail API quota has been exhausted";
            case BATCH_OPERATION_ERROR -> "Error occurred during batch operation processing";
            default -> null;
        };
    }
}
