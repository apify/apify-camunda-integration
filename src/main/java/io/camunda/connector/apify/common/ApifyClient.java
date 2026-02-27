package io.camunda.connector.apify.common;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client wrapper for Apify API interactions.
 * Holds the authentication token for the lifetime of the client instance.
 *
 * @throws ApifyClientException for HTTP errors with status code information
 * @throws IOException for network/IO level failures
 */
public class ApifyClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApifyClient.class);
    private static final String APIFY_API_URL = "https://api.apify.com";

    private static final int HTTP_NOT_FOUND = 404;

    private static final double DEFAULT_EXP_BACKOFF_INTERVAL = 1.0; // seconds
    private static final double DEFAULT_EXP_BACKOFF_EXPONENTIAL = 2.0;
    private static final int DEFAULT_EXP_BACKOFF_RETRIES = 3;

    private final CloseableHttpClient httpClient;
    private final String authToken;

    public ApifyClient(String authToken) {
        this.httpClient = HttpClients.createDefault();
        this.authToken = authToken;
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    // ---- Actor operations ----

    /**
     * Runs an Apify Actor by its ID with optional parameters.
     *
     * @param actorId    The Actor ID (e.g. "username~actor-name" or "actorIdCode")
     * @param inputJson  The Actor input as JSON string; pass null for no input body
     * @param runOptions The run options (timeout, memory, build, waitForFinishSecs)
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult runActor(String actorId, String inputJson, RunOptions runOptions)
            throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL)
                    .setPath("/v2/acts/" + actorId + "/runs");

            if (runOptions != null) {
                if (runOptions.timeout != null) {
                    builder.setParameter("timeout", runOptions.timeout.toString());
                }
                if (runOptions.memory != null && !runOptions.memory.isBlank()) {
                    builder.setParameter("memory", runOptions.memory);
                }
                if (runOptions.build != null && !runOptions.build.isBlank()) {
                    builder.setParameter("build", runOptions.build);
                }
                if (runOptions.waitForFinishSecs != null && runOptions.waitForFinishSecs > 0) {
                    builder.setParameter("waitForFinish", runOptions.waitForFinishSecs.toString());
                }
            }

            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.POST, urlPath, inputJson);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for actor run request", e);
        }
    }

    /**
     * Gets actor details by its ID.
     *
     * @param actorId The Actor ID (e.g. "username~actor-name" or "actorIdCode")
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getActor(String actorId) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId, null);
    }

    /**
     * Gets the default build for an actor.
     *
     * @param actorId The Actor ID
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getDefaultBuild(String actorId) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId + "/builds/default", null);
    }

    /**
     * Gets build details by its ID.
     *
     * @param buildId The build ID
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getBuild(String buildId) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-builds/" + buildId, null);
    }

    // ---- Task operations ----

    /**
     * Runs an Actor task by its ID with optional parameters.
     *
     * @param taskId     The Task ID
     * @param inputJson  The Task input as JSON string; pass null for no input body
     *                   (uses task's default input)
     * @param runOptions The run options (timeout, memory, build, waitForFinishSecs)
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult runTask(String taskId, String inputJson, RunOptions runOptions)
            throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL)
                    .setPath("/v2/actor-tasks/" + taskId + "/runs");

            if (runOptions != null) {
                if (runOptions.timeout != null) {
                    builder.setParameter("timeout", runOptions.timeout.toString());
                }
                if (runOptions.memory != null && !runOptions.memory.isBlank()) {
                    builder.setParameter("memory", runOptions.memory);
                }
                if (runOptions.build != null && !runOptions.build.isBlank()) {
                    builder.setParameter("build", runOptions.build);
                }
                if (runOptions.waitForFinishSecs != null && runOptions.waitForFinishSecs > 0) {
                    builder.setParameter("waitForFinish", runOptions.waitForFinishSecs.toString());
                }
            }

            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.POST, urlPath, inputJson);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for task run request", e);
        }
    }

    /**
     * Gets task details by its ID.
     *
     * @param taskId The Task ID
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getTask(String taskId) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-tasks/" + taskId, null);
    }

    // ---- Run operations ----

    /**
     * Gets the status of an actor run by its ID.
     *
     * @param runId              The run ID
     * @param waitForFinishSecs  Optional seconds to wait for the run to finish
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getRunStatus(String runId, Integer waitForFinishSecs) throws IOException {
        String urlPath = "/v2/actor-runs/" + runId;
        if (waitForFinishSecs != null && waitForFinishSecs > 0) {
            urlPath += "?waitForFinish=" + waitForFinishSecs.toString();
        }
        return executeRequest(Method.GET, urlPath, null);
    }

    // ---- Dataset operations ----

    /**
     * Gets items from a dataset with optional pagination parameters.
     * Always returns data in JSON format.
     *
     * @param datasetId The ID of the dataset
     * @param offset    The number of items to skip. Defaults to 0.
     * @param limit     The maximum number of items to return. No limit by default.
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getDatasetItems(String datasetId, Integer offset, Integer limit)
            throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL)
                    .setPath("/v2/datasets/" + datasetId + "/items")
                    .setParameter("format", "json");

            if (offset != null) {
                builder.setParameter("offset", offset.toString());
            }
            if (limit != null) {
                builder.setParameter("limit", limit.toString());
            }

            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.GET, urlPath, null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for dataset items request", e);
        }
    }

    // ---- Key-value store operations ----

    /**
     * Gets a record from a key-value store by store ID and record key.
     *
     * @param storeId   The ID of the key-value store
     * @param recordKey The key of the record to retrieve
     * @return ResponseResult containing status code, response body, binary data,
     *         and content type
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult getKeyValueStoreRecord(String storeId, String recordKey)
            throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL)
                    .setPath("/v2/key-value-stores/" + storeId + "/records/" + recordKey);

            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.GET, urlPath, null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for key-value store record request", e);
        }
    }

    // ---- Webhook operations ----

    /**
     * Creates a new webhook in Apify.
     *
     * @param webhookJson The webhook configuration as JSON string
     * @return ResponseResult containing the created webhook details
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult createWebhook(String webhookJson) throws IOException {
        return executeRequest(Method.POST, "/v2/webhooks", webhookJson);
    }

    /**
     * Deletes a webhook from Apify by its ID.
     * DELETE is idempotent: a 404 means the webhook is already gone, which is treated as success.
     *
     * @param webhookId The ID of the webhook to delete
     * @return ResponseResult containing the deletion confirmation, or null if the webhook was already deleted (404)
     * @throws ApifyClientException if the API returns a non-success, non-404 HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult deleteWebhook(String webhookId) throws IOException {
        try {
            return executeRequest(Method.DELETE, "/v2/webhooks/" + webhookId, null);
        } catch (ApifyClientException e) {
            if (e.getStatusCode() == HTTP_NOT_FOUND) {
                LOGGER.info("Webhook {} already deleted, treating as success.", webhookId);
                return null;
            }
            throw e;
        }
    }

    /**
     * Lists all webhooks for the authenticated user.
     *
     * @return ResponseResult containing the webhooks list as JSON
     * @throws ApifyClientException if the API returns a non-success HTTP status
     * @throws IOException if the request fails at the network level
     */
    public ResponseResult listWebhooks() throws IOException {
        return executeRequest(Method.GET, "/v2/webhooks", null);
    }

    // ---- Internal HTTP infrastructure ----

    private ResponseResult executeRequest(Method method, String urlPath, String body)
            throws IOException {
        URI baseUri = URI.create(APIFY_API_URL);
        URI fullUri = baseUri.resolve(urlPath);
        String fullUrl = fullUri.toString();

        return retryWithExponentialBackoff(method, fullUrl, body);
    }

    private ResponseResult retryWithExponentialBackoff(Method method, String url, String body)
            throws IOException {
        IOException lastError = null;

        for (int i = 0; i < DEFAULT_EXP_BACKOFF_RETRIES; i++) {
            try {
                ResponseResult result = performHttpRequest(method, url, body);
                int statusCode = result.statusCode;
                String responseBody = result.responseBody;

                if (statusCode >= 200 && statusCode < 300) {
                    return result;
                } else {
                    throw new ApifyClientException(
                            String.format(
                                    "HTTP %s request to %s failed with status %d: %s",
                                    method, url, statusCode, responseBody),
                            statusCode);
                }
            } catch (ApifyClientException e) {
                lastError = e;
                int statusCode = e.getStatusCode();

                if (isStatusCodeRetryable(statusCode)) {
                    double sleepTimeSecs = DEFAULT_EXP_BACKOFF_INTERVAL * Math.pow(DEFAULT_EXP_BACKOFF_EXPONENTIAL, i);
                    long sleepTimeMs = (long) (sleepTimeSecs * 1000);

                    try {
                        Thread.sleep(sleepTimeMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }

                    continue;
                }
                throw e;
            } catch (IOException e) {
                ApifyClientException wrappedException = new ApifyClientException(
                        String.format(
                                "HTTP %s request to %s failed: %s",
                                method, url, e.getMessage()),
                        0, e);
                lastError = wrappedException;

                if (i < DEFAULT_EXP_BACKOFF_RETRIES - 1) {
                    double sleepTimeSecs = DEFAULT_EXP_BACKOFF_INTERVAL * Math.pow(DEFAULT_EXP_BACKOFF_EXPONENTIAL, i);
                    long sleepTimeMs = (long) (sleepTimeSecs * 1000);

                    try {
                        Thread.sleep(sleepTimeMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }

                    continue;
                }
                throw wrappedException;
            } catch (RuntimeException e) {
                throw new IOException(
                        String.format(
                                "Unexpected error during HTTP %s request to %s: %s",
                                method, url, e.getMessage()),
                        e);
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException(
                String.format(
                        "HTTP %s request to %s failed after %d retries",
                        method, url, DEFAULT_EXP_BACKOFF_RETRIES));
    }

    private ResponseResult performHttpRequest(Method method, String url, String body)
            throws IOException {
        ClassicHttpRequest request;

        switch (method) {
            case GET:
                request = new HttpGet(url);
                break;
            case POST:
                request = new HttpPost(url);
                if (body != null && !body.isEmpty()) {
                    ((HttpPost) request).setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                }
                break;
            case DELETE:
                request = new HttpDelete(url);
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        addHeaders(request);

        HttpClientResponseHandler<ResponseResult> responseHandler = (ClassicHttpResponse response) -> {
            int statusCode = response.getCode();
            String responseBody = "";
            byte[] responseBodyBytes = new byte[0];
            String contentType = "application/octet-stream";

            if (response.getEntity() != null) {
                if (response.getEntity().getContent() != null) {
                    responseBodyBytes = response.getEntity().getContent().readAllBytes();
                    responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
                }

                String contentTypeHeader = response.getEntity().getContentType();
                if (contentTypeHeader != null && !contentTypeHeader.isEmpty()) {
                    if (contentTypeHeader.contains(";")) {
                        contentType = contentTypeHeader.substring(0, contentTypeHeader.indexOf(";")).trim();
                    } else {
                        contentType = contentTypeHeader.trim();
                    }
                }
            }

            return new ResponseResult(statusCode, responseBody, responseBodyBytes, contentType);
        };

        return httpClient.execute(null, request, responseHandler);
    }

    private void addHeaders(HttpRequest request) {
        if (authToken != null && !authToken.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + authToken);
        }
        request.setHeader("x-apify-integration-platform", "camunda");
    }

    /**
     * Checks if the given status code is retryable.
     * Status codes 429 (rate limit) and 500+ are retried.
     * Other status codes 300-499 (except 429) are not retried,
     * because the error is probably caused by invalid URL (redirect 3xx) or invalid
     * user input (4xx).
     */
    private boolean isStatusCodeRetryable(int statusCode) {
        boolean isRateLimitError = statusCode == HttpStatus.SC_TOO_MANY_REQUESTS;
        boolean isInternalError = statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
        return isRateLimitError || isInternalError;
    }

    // ---- Inner classes ----

    /**
     * Result class to hold response status code and body.
     */
    public static class ResponseResult {
        private final int statusCode;
        private final String responseBody;
        private final byte[] responseBodyInBytes;
        private final String contentType;

        ResponseResult(int statusCode, String responseBody, byte[] responseBodyBytes, String contentType) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.responseBodyInBytes = responseBodyBytes;
            this.contentType = contentType;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public byte[] getResponseBodyInBytes() {
            return responseBodyInBytes;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
