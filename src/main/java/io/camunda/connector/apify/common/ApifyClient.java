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
import java.util.Objects;

/**
 * HTTP client wrapper for Apify API interactions.
 * Holds the authentication token for the lifetime of the client instance.
 * Methods throw {@link IOException} for HTTP errors and network failures.
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

    /**
     * Creates a new client bound to the given authentication token.
     *
     * @param authToken The Apify API token used for all requests; must not be null or blank
     */
    public ApifyClient(String authToken) {
        Objects.requireNonNull(authToken, "authToken must not be null");
        if (authToken.isBlank()) throw new IllegalArgumentException("authToken must not be blank");
        this.httpClient = HttpClients.createDefault();
        this.authToken = authToken;
    }

    /** Package-private constructor for unit testing with a pre-configured HTTP client. */
    ApifyClient(String authToken, CloseableHttpClient httpClient) {
        Objects.requireNonNull(authToken, "authToken must not be null");
        if (authToken.isBlank()) throw new IllegalArgumentException("authToken must not be blank");
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.httpClient = httpClient;
        this.authToken = authToken;
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    // ---- Actor operations ----

    /**
     * Starts an actor run with optional input and run configuration.
     *
     * @param actorId    The actor ID or {@code username~actor-name} slug
     * @param inputJson  Input JSON body; pass {@code null} to use the actor's default input
     * @param runOptions Optional timeout, memory, build, and waitForFinish overrides; may be {@code null}
     * @return The run resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult runActor(String actorId, String inputJson, RunOptions runOptions)
            throws IOException {
        return executeRunRequest("/v2/acts/" + actorId + "/runs", inputJson, runOptions);
    }

    /**
     * Fetches actor details by ID.
     *
     * @param actorId The actor ID or {@code username~actor-name} slug
     * @return The actor resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getActor(String actorId) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId, null);
    }

    /**
     * Fetches the default build of an actor.
     *
     * @param actorId The actor ID or {@code username~actor-name} slug
     * @return The build resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getDefaultBuild(String actorId) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId + "/builds/default", null);
    }

    /**
     * Fetches a specific build by its ID.
     *
     * @param buildId The build ID
     * @return The build resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getBuild(String buildId) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-builds/" + buildId, null);
    }

    // ---- Task operations ----

    /**
     * Starts an actor task run with optional input and run configuration.
     *
     * @param taskId     The task ID or {@code username~task-name} slug
     * @param inputJson  Input JSON body; pass {@code null} to use the task's saved default input
     * @param runOptions Optional timeout, memory, build, and waitForFinish overrides; may be {@code null}
     * @return The run resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult runTask(String taskId, String inputJson, RunOptions runOptions)
            throws IOException {
        return executeRunRequest("/v2/actor-tasks/" + taskId + "/runs", inputJson, runOptions);
    }

    /**
     * Fetches task details by ID.
     *
     * @param taskId The task ID or {@code username~task-name} slug
     * @return The task resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getTask(String taskId) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-tasks/" + taskId, null);
    }

    // ---- Run operations ----

    /**
     * Fetches the current status of a run.
     * When {@code waitForFinishSecs} is positive the API long-polls for up to that many seconds
     * before returning, avoiding repeated short polls from the caller.
     *
     * @param runId             The run ID
     * @param waitForFinishSecs Seconds to long-poll for completion; {@code null} or {@code <= 0} returns immediately
     * @return The run resource as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getRunStatus(String runId, Integer waitForFinishSecs) throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL)
                    .setPath("/v2/actor-runs/" + runId);
            if (waitForFinishSecs != null && waitForFinishSecs > 0) {
                builder.setParameter("waitForFinish", waitForFinishSecs.toString());
            }
            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.GET, urlPath, null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for run status request", e);
        }
    }

    // ---- Dataset operations ----

    /**
     * Fetches items from a dataset in JSON format with optional pagination.
     *
     * @param datasetId The dataset ID
     * @param offset    Number of items to skip from the start; may be {@code null}
     * @param limit     Maximum number of items to return; may be {@code null}
     * @return The dataset items as a JSON array
     * @throws IOException if the HTTP request fails
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
     * Fetches a single record from a key-value store.
     *
     * @param storeId   The key-value store ID
     * @param recordKey The record key
     * @return The record, preserving the original content-type from the API response
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult getKeyValueStoreRecord(String storeId, String recordKey)
            throws IOException {
        return executeRequest(Method.GET, "/v2/key-value-stores/" + storeId + "/records/" + recordKey, null);
    }

    // ---- Webhook operations ----

    /**
     * Creates a new webhook.
     *
     * @param webhookJson The webhook configuration as a JSON string
     * @return The created webhook resource
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult createWebhook(String webhookJson) throws IOException {
        return executeRequest(Method.POST, "/v2/webhooks", webhookJson);
    }

    /**
     * Deletes a webhook by ID.
     * DELETE is idempotent: a 404 response means the webhook is already gone and is treated as
     * success, returning a sentinel {@code ResponseResult} with status 404 and an empty body.
     *
     * @param webhookId The ID of the webhook to delete
     * @return The deletion confirmation, or a sentinel 404 result if the webhook was already deleted
     * @throws IOException if the request fails with any status other than 404
     */
    public ResponseResult deleteWebhook(String webhookId) throws IOException {
        try {
            return executeRequest(Method.DELETE, "/v2/webhooks/" + webhookId, null);
        } catch (HttpRequestException e) {
            if (e.getStatusCode() == HTTP_NOT_FOUND) {
                LOGGER.info("Webhook {} already deleted, treating as success.", webhookId);
                return new ResponseResult(HTTP_NOT_FOUND, "", new byte[0], "application/json");
            }
            throw e;
        }
    }

    /**
     * Lists all webhooks for the authenticated user.
     *
     * @return The webhooks list as returned by the Apify API
     * @throws IOException if the HTTP request fails
     */
    public ResponseResult listWebhooks() throws IOException {
        return executeRequest(Method.GET, "/v2/webhooks", null);
    }

    // ---- Internal HTTP infrastructure ----

    private ResponseResult executeRunRequest(String path, String inputJson, RunOptions runOptions)
            throws IOException {
        try {
            URIBuilder builder = new URIBuilder(APIFY_API_URL).setPath(path);
            applyRunOptions(builder, runOptions);

            URI uri = builder.build();
            String urlPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
            return executeRequest(Method.POST, urlPath, inputJson);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for run request: " + path, e);
        }
    }

    private void applyRunOptions(URIBuilder builder, RunOptions runOptions) {
        if (runOptions == null) {
            return;
        }
        if (runOptions.timeout() != null) {
            builder.setParameter("timeout", runOptions.timeout().toString());
        }
        if (runOptions.memory() != null && !runOptions.memory().isBlank()) {
            builder.setParameter("memory", runOptions.memory());
        }
        if (runOptions.build() != null && !runOptions.build().isBlank()) {
            builder.setParameter("build", runOptions.build());
        }
        if (runOptions.waitForFinishSecs() != null && runOptions.waitForFinishSecs() > 0) {
            builder.setParameter("waitForFinish", runOptions.waitForFinishSecs().toString());
        }
    }

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
                int statusCode = result.statusCode();
                String responseBody = result.responseBody();

                if (statusCode >= 200 && statusCode < 300) {
                    return result;
                } else {
                    throw new HttpRequestException(
                            String.format(
                                    "HTTP %s request to %s failed with status %d: %s",
                                    method, url, statusCode, responseBody),
                            statusCode);
                }
            } catch (HttpRequestException e) {
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
                lastError = e;

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
                throw new IOException(
                        String.format("HTTP %s request to %s failed: %s",
                                method, url, e.getMessage()),
                        e);
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
        ClassicHttpRequest request = switch (method) {
            case GET -> new HttpGet(url);
            case POST -> {
                var post = new HttpPost(url);
                if (body != null && !body.isEmpty()) {
                    post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                }
                yield post;
            }
            case DELETE -> new HttpDelete(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

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
        request.setHeader("Authorization", "Bearer " + authToken);
        request.setHeader("x-apify-integration-platform", "camunda");
    }

    private boolean isStatusCodeRetryable(int statusCode) {
        boolean isRateLimitError = statusCode == HttpStatus.SC_TOO_MANY_REQUESTS;
        boolean isInternalError = statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
        return isRateLimitError || isInternalError;
    }

    // ---- Inner classes ----

    /**
     * Immutable holder for an HTTP response from the Apify API.
     */
    public record ResponseResult(
            int statusCode,
            String responseBody,
            byte[] responseBodyInBytes,
            String contentType
    ) {}

    /** Carries HTTP status code information for retry logic. */
    private static class HttpRequestException extends IOException {
        private final int statusCode;

        public HttpRequestException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
