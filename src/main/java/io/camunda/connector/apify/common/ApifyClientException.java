package io.camunda.connector.apify.common;

import java.io.IOException;
import java.util.Set;

/**
 * Exception thrown by {@link ApifyClient} when an HTTP request to the Apify API fails
 * with a non-success status code. Carries the HTTP status code so callers can distinguish
 * user-correctable errors (bad input, auth issues) from transient/server errors.
 */
public class ApifyClientException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Sentinel value used when an exception originates from a network/IO failure
     * and no HTTP response was received (i.e. there is no real HTTP status code).
     */
    public static final int NO_HTTP_STATUS = 0;

    /**
     * Status codes for user input or credential errors (400–404, 422).
     * Other 4xx codes are intentionally ignored as unexpected.
     */
    private static final Set<Integer> LIKELY_USER_ERROR_CODES = Set.of(400, 401, 402, 403, 404, 422);

    private final int statusCode;

    public ApifyClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApifyClientException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns {@code true} if the HTTP status code indicates the error is likely caused
     * by the user's input (bad request, authentication, payment, authorization, or not found).
     * These errors are not retryable and should surface as connector input errors.
     */
    public boolean isLikelyUserError() {
        return LIKELY_USER_ERROR_CODES.contains(statusCode);
    }
}
