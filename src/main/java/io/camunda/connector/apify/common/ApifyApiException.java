package io.camunda.connector.apify.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Thrown when the Apify API returns a non-2xx response or the request fails at
 * the transport layer. Carries the HTTP status code and, when the response body
 * matches Apify's standard error envelope ({@code {"error":{"type":...,"message":...}}}),
 * the parsed {@code type} and {@code message} so callers can render a clean,
 * user-facing error without the raw HTTP noise.
 */
public class ApifyApiException extends IOException {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int statusCode;
    private final String errorType;
    private final String errorMessage;

    public ApifyApiException(String message, int statusCode, String errorType, String errorMessage) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public ApifyApiException(String message, int statusCode, String errorType, String errorMessage, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /**
     * Parses Apify's standard error envelope ({@code {"error":{"type":...,"message":...}}})
     * from the response body and produces an {@link ApifyApiException} with a clean,
     * user-facing message. Falls back to a generic message when the body is not in
     * Apify's envelope format.
     *
     * @param statusCode  the HTTP status code
     * @param responseBody the raw response body (may be {@code null})
     */
    static ApifyApiException fromApiResponse(int statusCode, String responseBody) {
        String apifyType = null;
        String apifyMessage = null;
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = MAPPER.readTree(responseBody);
                JsonNode errorNode = root.path("error");
                if (errorNode.isObject()) {
                    JsonNode typeNode = errorNode.path("type");
                    JsonNode messageNode = errorNode.path("message");
                    if (typeNode.isTextual()) {
                        apifyType = typeNode.asText();
                    }
                    if (messageNode.isTextual()) {
                        apifyMessage = messageNode.asText();
                    }
                }
            } catch (IOException ignored) {
                // Body wasn't JSON or didn't match the envelope
            }
        }

        String message;
        if (apifyMessage != null) {
            message = apifyType != null
                    ? String.format("Apify API error (%d %s): %s", statusCode, apifyType, apifyMessage)
                    : String.format("Apify API error (%d): %s", statusCode, apifyMessage);
        } else {
            String snippet = responseBody == null ? "" : responseBody.trim();
            if (snippet.length() > 300) {
                snippet = snippet.substring(0, 300) + "...";
            }
            message = snippet.isEmpty()
                    ? String.format("Apify API error (%d)", statusCode)
                    : String.format("Apify API error (%d): %s", statusCode, snippet);
        }
        return new ApifyApiException(message, statusCode, apifyType, apifyMessage);
    }

    /**
     * Builds the error for a failed Web Fetch Standby request. The Actor reports its own errors
     * as {@code {"error": "...", "code": "..."}}, the Standby gateway uses Apify's standard
     * envelope, and a gateway error page may not be JSON at all.
     *
     * @param statusCode   the HTTP status code
     * @param responseBody the raw response body (may be {@code null})
     */
    static ApifyApiException fromWebFetchResponse(int statusCode, String responseBody) {
        String prefix = String.format("Web Fetch failed with status %d", statusCode);
        String errorType = null;
        String errorMessage = null;
        String errorCode = null;
        JsonNode root = null;
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                root = MAPPER.readTree(responseBody);
            } catch (IOException ignored) {
                // Not JSON, e.g. a gateway error page
            }
        }
        if (root != null && root.isObject()) {
            JsonNode errorNode = root.path("error");
            if (errorNode.isTextual()) {
                errorMessage = errorNode.asText();
                errorCode = root.path("code").isTextual() ? root.path("code").asText() : null;
            } else if (errorNode.isObject()) {
                errorType = errorNode.path("type").isTextual() ? errorNode.path("type").asText() : null;
                errorMessage = errorNode.path("message").isTextual() ? errorNode.path("message").asText() : null;
            }
        }

        String message;
        if (errorMessage != null) {
            message = errorCode != null
                    ? String.format("%s: %s (code: %s)", prefix, errorMessage, errorCode)
                    : String.format("%s: %s", prefix, errorMessage);
        } else {
            String snippet = responseBody == null ? "" : responseBody.trim();
            if (snippet.length() > 300) {
                snippet = snippet.substring(0, 300) + "...";
            }
            message = snippet.isEmpty() ? prefix : String.format("%s: %s", prefix, snippet);
        }
        return new ApifyApiException(message, statusCode, errorType, errorMessage);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getApifyErrorMessage() {
        return errorMessage;
    }

    public boolean isInvalidInput() {
        return "invalid-input".equals(errorType);
    }
}
