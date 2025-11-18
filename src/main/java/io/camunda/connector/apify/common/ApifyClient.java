package io.camunda.connector.apify.common;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client wrapper for Apify API interactions.
 * Provides a simple interface for making HTTP requests to the Apify API.
 */
public class ApifyClient implements AutoCloseable {
    
    private static final String APIFY_API_URL = "https://api.apify.com";
    
    // Exponential backoff constants
    private static final double DEFAULT_EXP_BACKOFF_INTERVAL = 1.0; // seconds
    private static final double DEFAULT_EXP_BACKOFF_EXPONENTIAL = 2.0;
    private static final int DEFAULT_EXP_BACKOFF_RETRIES = 3;
    
    private final CloseableHttpClient httpClient;
    
    public ApifyClient() {
        this.httpClient = HttpClients.createDefault();
    }
    
    /**
     * Executes an HTTP request with the specified method, URL path, authentication token, and optional body.
     * 
     * @param method The HTTP method (GET, POST, etc.)
     * @param urlPath The URL path to append to the base API URL (e.g., "/v2/actors")
     * @param authToken The authentication token
     * @param body The request body (null for GET requests or when no body is needed)
     * @return The response body as a string
     * @throws IOException if the request fails after all retries
     */
    private String executeRequest(Method method, String urlPath, String authToken, String body) throws IOException {
        URI baseUri = URI.create(APIFY_API_URL);
        URI fullUri = baseUri.resolve(urlPath);
        String fullUrl = fullUri.toString();
        
        return retryWithExponentialBackoff(method, fullUrl, authToken, body);
    }
    
    /**
     * Executes an HTTP request with exponential backoff retry logic.
     * If request fails with HTTP code 500+ or 429 (rate limit), it is retried
     * with exponential backoff: interval * exponential^attempt (1s, 2s, 4s, ...) up to maxRetries.
     * 
     * @param method The HTTP method (GET, POST, etc.)
     * @param url The full URL to request
     * @param authToken The authentication token
     * @param body The request body (null for GET requests or when no body is needed)
     * @return The response body as a string
     * @throws IOException if the request fails after all retries or if a non-retryable error occurs
     */
    private String retryWithExponentialBackoff(Method method, String url, String authToken, String body) throws IOException {
        IOException lastError = null;
        
        for (int i = 0; i < DEFAULT_EXP_BACKOFF_RETRIES; i++) {
            try {
                ResponseResult result = performHttpRequest(method, url, authToken, body);
                int statusCode = result.statusCode;
                String responseBody = result.responseBody;
                
                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    // Create an exception with status code information for retry logic
                    HttpRequestException exception = new HttpRequestException(
                        String.format(
                            "HTTP %s request to %s failed with status %d: %s",
                            method, url, statusCode, responseBody
                        ),
                        statusCode
                    );
                    throw exception;
                }
            } catch (HttpRequestException e) {
                lastError = e;
                int statusCode = e.getStatusCode();
                
                if (isStatusCodeRetryable(statusCode)) {
                    // Generate sleep time: interval * exponential^attempt
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
                // Non-retryable error - throw immediately
                throw e;
            } catch (IOException e) {
                // Wrap other IOException to include status code
                HttpRequestException wrappedException = new HttpRequestException(
                    String.format(
                        "HTTP %s request to %s failed: %s",
                        method, url, e.getMessage()
                    ),
                    0, e);
                lastError = wrappedException;
                
                // For network errors, retry once more
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
                // Wrap unexpected runtime exceptions
                throw new IOException(
                    String.format(
                        "Unexpected error during HTTP %s request to %s: %s",
                        method, url, e.getMessage()
                    ),
                    e
                );
            }
        }
        
        // All retries exhausted - throw the last error
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException(
            String.format(
                "HTTP %s request to %s failed after %d retries",
                method, url, DEFAULT_EXP_BACKOFF_RETRIES
            )
        );
    }
    
    /**
     * Result class to hold response status code and body.
     */
    private static class ResponseResult {
        final int statusCode;
        final String responseBody;
        
        ResponseResult(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }
    
    private ResponseResult performHttpRequest(Method method, String url, String authToken, String body) throws IOException {
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
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        addHeaders(request, authToken);
        
        // Use execute() with ResponseHandler for automatic resource management
        // This ensures the response and connection are properly closed even if exceptions occur
        HttpClientResponseHandler<ResponseResult> responseHandler = (ClassicHttpResponse response) -> {
            int statusCode = response.getCode();
            String responseBody = "";
            
            if (response.getEntity() != null && response.getEntity().getContent() != null) {
                responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            }
            
            return new ResponseResult(statusCode, responseBody);
        };
        
        return httpClient.execute(null, request, responseHandler);
    }
    
    private void addHeaders(org.apache.hc.core5.http.HttpRequest request, String authToken) {
        // Add authentication header
        if (authToken != null && !authToken.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + authToken);
        }
        
        // Add platform header
        request.setHeader("x-apify-integration-platform", "camunda");
    }
    
    /**
     * Gets items from a dataset with optional pagination parameters.
     * Always returns data in JSON format.
     * 
     * @param datasetId The ID of the dataset
     * @param authToken The authentication token
     * @param offset The number of items to skip. Defaults to 0.
     * @param limit The maximum number of items to return. No limit by default.
     * @return The dataset items as a JSON string
     * @throws IOException if the request fails
     */
    public String getDatasetItems(String datasetId, String authToken, Integer offset, Integer limit) throws IOException {
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
            return executeRequest(Method.GET, urlPath, authToken, null);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for dataset items request", e);
        }
    }
    
    /**
     * Checks if the given status code is retryable.
     * Status codes 429 (rate limit) and 500+ are retried.
     * Other status codes 300-499 (except 429) are not retried,
     * because the error is probably caused by invalid URL (redirect 3xx) or invalid user input (4xx).
     * 
     * @param statusCode The HTTP status code
     * @return true if the status code is retryable, false otherwise
     */
    private boolean isStatusCodeRetryable(int statusCode) {
        boolean isRateLimitError = statusCode == HttpStatus.SC_TOO_MANY_REQUESTS;
        boolean isInternalError = statusCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
        return isRateLimitError || isInternalError;
        }
        
    /**
     * Runs an Apify Actor by its ID with optional parameters.
     * Sends a POST request to /v2/acts/{actorId}/runs with the provided input JSON and query parameters.
     *
     * @param authToken The authentication token
     * @param actorId The Actor ID (e.g. "username/actor-name" or "actorIdCode")
     * @param inputJson The Actor input as JSON string; pass null for no input body
     * @param runOptions The run options (timeout, memory, build, waitForFinishSecs)
     * @return The response body as a JSON string (Actor run object)
     * @throws IOException if the request fails
     */
    public String runActor(String authToken, String actorId, String inputJson, RunOptions runOptions) throws IOException {
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
            return executeRequest(Method.POST, urlPath, authToken, inputJson);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for actor run request", e);
        }
    }

    /**
     * Runs an Actor task by its ID with optional parameters.
     * Sends a POST request to /v2/actor-tasks/{taskId}/runs with the provided input JSON and query parameters.
     * 
     * @param authToken The authentication token
     * @param taskId The Task ID
     * @param inputJson The Task input as JSON string; pass null for no input body (uses task's default input)
     * @param runOptions The run options (timeout, memory, build, waitForFinishSecs)
     * @return The response body as a JSON string (Actor run object)
     * @throws IOException if the request fails
     */
    public String runTask(String authToken, String taskId, String inputJson, RunOptions runOptions) throws IOException {
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
            return executeRequest(Method.POST, urlPath, authToken, inputJson);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for task run request", e);
        }
    }

    /**
     * Gets the status of an actor run by its ID.
     * 
     * @param runId The run ID
     * @param authToken The authentication token
     * @return The response body as a JSON string (Actor run object with current status)
     * @throws IOException if the request fails
     */
    public String getRunStatus(String runId, String authToken, Integer waitForFinishSecs) throws IOException {
        String urlPath = "/v2/actor-runs/" + runId;
        if (waitForFinishSecs != null && waitForFinishSecs > 0) {
            urlPath += "?waitForFinish=" + waitForFinishSecs.toString();
        }
        return executeRequest(Method.GET, urlPath, authToken, null);
    }

    /**
     * Gets actor details by its ID.
     * 
     * @param actorId The Actor ID (e.g. "username/actor-name" or "actorIdCode")
     * @param authToken The authentication token
     * @return The response body as a JSON string (Actor object)
     * @throws IOException if the request fails
     */
    public String getActor(String actorId, String authToken) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId, authToken, null);
    }

        /**
     * Gets task details by its ID.
     * 
     * @param taskId The Task ID
     * @param authToken The authentication token
     * @return The response body as a JSON string (Task object)
     * @throws IOException if the request fails
     */
    public String getTask(String taskId, String authToken) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-tasks/" + taskId, authToken, null);
    }

    /**
     * Gets build details by its ID.
     * 
     * @param buildId The build ID
     * @param authToken The authentication token
     * @return The response body as a JSON string (Build object)
     * @throws IOException if the request fails
     */
    public String getBuild(String buildId, String authToken) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-builds/" + buildId, authToken, null);
    }

    /**
     * Gets the default build for an actor.
     * 
     * @param actorId The Actor ID
     * @param authToken The authentication token
     * @return The response body as a JSON string (Build object)
     * @throws IOException if the request fails
     */
    public String getDefaultBuild(String actorId, String authToken) throws IOException {
        return executeRequest(Method.GET, "/v2/acts/" + actorId + "/builds/default", authToken, null);
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
    
    /**
     * Custom exception class to carry HTTP status code information for retry logic.
     */
    private static class HttpRequestException extends IOException {
        private final int statusCode;
        
        public HttpRequestException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
        
        public HttpRequestException(String message, int statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
    }
}
