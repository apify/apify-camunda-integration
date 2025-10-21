package io.camunda.connector.apify.common;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client wrapper for Apify API interactions.
 * Provides a simple interface for making HTTP requests to the Apify API.
 */
public class ApifyClient implements AutoCloseable {
    
    private static final String APIFY_API_URL = "https://api.apify.com";
    
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
     * @throws IOException if the request fails
     */
    private String executeRequest(Method method, String urlPath, String authToken, String body) throws IOException {
        String fullUrl = APIFY_API_URL + (urlPath.startsWith("/") ? urlPath : "/" + urlPath);
        
        try (ClassicHttpResponse response = performHttpRequest(method, fullUrl, authToken, body)) {
            int statusCode = response.getCode();
            String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            
            
            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new IOException("HTTP request failed with status " + statusCode + ": " + responseBody);
            }
        }
    }
    
    private ClassicHttpResponse performHttpRequest(Method method, String url, String authToken, String body) throws IOException {
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
        return httpClient.executeOpen(null, request, null);
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
     * Gets items from a dataset with optional parameters.
     * Always returns data in JSON format.
     * 
     * @param datasetId The ID of the dataset
     * @param authToken The authentication token
     * @param offset The number of items to skip. Defaults to 0.
     * @param limit The maximum number of items to return. No limit by default.
     * @param desc Whether to return items in descending order (newest first). Defaults to false.
     * @return The dataset items as a JSON string
     * @throws IOException if the request fails
     */
    public String getDatasetItems(String datasetId, String authToken, Integer offset, Integer limit) throws IOException {
        StringBuilder urlPath = new StringBuilder("/v2/datasets/").append(datasetId).append("/items");
        
        // Always use JSON format
        urlPath.append("?format=json");
        
        // Add other query parameters if provided
        if (offset != null) {
            urlPath.append("&offset=").append(offset);
        }
        if (limit != null) {
            urlPath.append("&limit=").append(limit);
        }
        
        return executeRequest(Method.GET, urlPath.toString(), authToken, null);
    }
    
    /**
     * Runs an Apify Actor by its ID with optional parameters.
     * Sends a POST request to /v2/acts/{actorId}/runs with the provided input JSON and query parameters.
     *
     * @param actorId The Actor ID (e.g. "username/actor-name" or "actorIdCode")
     * @param authToken The authentication token
     * @param inputJson The Actor input as JSON string; pass null for no input body
     * @param timeout Timeout in seconds; null for default
     * @param memory Memory in MB as string; null or empty for default
     * @param build Build number or tag; null for default
     * @param waitForFinishSecs Number of seconds to wait synchronously for the run to finish; null to not wait
     * @return The response body as a JSON string (Actor run object)
     * @throws IOException if the request fails
     */
    public String runActor(String actorId, String authToken, String inputJson, Integer timeout, String memory, String build, Integer waitForFinishSecs) throws IOException {
        StringBuilder urlPath = new StringBuilder("/v2/acts/").append(actorId).append("/runs");
        
        // Build query parameters
        StringBuilder queryParams = new StringBuilder();
        if (timeout != null) {
            queryParams.append("timeout=").append(timeout);
        }
        if (memory != null && !memory.trim().isEmpty()) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("memory=").append(memory);
        }
        if (build != null) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("build=").append(build);
        }
        if (waitForFinishSecs != null && waitForFinishSecs > 0) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("waitForFinish=").append(waitForFinishSecs);
        }
        
        if (queryParams.length() > 0) {
            urlPath.append("?").append(queryParams);
        }
        
        return executeRequest(Method.POST, urlPath.toString(), authToken, inputJson);
    }

    /**
     * Gets the status of an actor run by its ID.
     * 
     * @param runId The run ID
     * @param authToken The authentication token
     * @return The response body as a JSON string (Actor run object with current status)
     * @throws IOException if the request fails
     */
    public String getRunStatus(String runId, String authToken) throws IOException {
        return executeRequest(Method.GET, "/v2/actor-runs/" + runId, authToken, null);
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
     * Runs an Actor task by its ID with optional parameters.
     * Sends a POST request to /v2/actor-tasks/{taskId}/runs with the provided input JSON and query parameters.
     * 
     * @param taskId The Task ID
     * @param authToken The authentication token
     * @param inputJson The Task input as JSON string; pass null for no input body (uses task's default input)
     * @param timeout Timeout in seconds; null for default
     * @param memory Memory in MB as string; null or empty for default
     * @param build Build number or tag; null for default
     * @param waitForFinishSecs Number of seconds to wait synchronously for the run to finish; null to not wait
     * @return The response body as a JSON string (Actor run object)
     * @throws IOException if the request fails
     */
    public String runTask(String taskId, String authToken, String inputJson, Integer timeout, String memory, String build, Integer waitForFinishSecs) throws IOException {
        StringBuilder urlPath = new StringBuilder("/v2/actor-tasks/").append(taskId).append("/runs");
        
        // Build query parameters
        StringBuilder queryParams = new StringBuilder();
        if (timeout != null) {
            queryParams.append("timeout=").append(timeout);
        }
        if (memory != null && !memory.trim().isEmpty()) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("memory=").append(memory);
        }
        if (build != null) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("build=").append(build);
        }
        if (waitForFinishSecs != null && waitForFinishSecs > 0) {
            if (queryParams.length() > 0) queryParams.append("&");
            queryParams.append("waitForFinish=").append(waitForFinishSecs);
        }
        
        if (queryParams.length() > 0) {
            urlPath.append("?").append(queryParams);
        }
        
        return executeRequest(Method.POST, urlPath.toString(), authToken, inputJson);
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
