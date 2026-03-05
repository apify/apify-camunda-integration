package io.camunda.connector.apify.common;

import java.io.IOException;
import java.util.Set;

/**
 * Exception thrown by {@link ApifyClient} when an HTTP request to the Apify API fails
 * with a non-success status code. Carries the HTTP status code so callers can distinguish
 * user-correctable errors (bad input, auth issues) from transient/server errors.
 */
public class ApifyClientException extends IOException {

    // 402 (Payment Required) is included because Apify returns it when the account's
    // usage limit is exceeded; a billing issue the user must fix, not a transient error.
    private static final Set<Integer> LIKELY_USER_ERROR_CODES = Set.of(400, 401, 402, 403, 404);

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
