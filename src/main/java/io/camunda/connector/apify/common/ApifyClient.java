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
        StringBuilder urlPath = new StringBuilder("/v2/datasets/").append(datasetId).append("/items");
        
        // Always use JSON format
        urlPath.append("?format=json");
        
        // Add pagination parameters if provided
        if (offset != null) {
            urlPath.append("&offset=").append(offset);
        }
        if (limit != null) {
            urlPath.append("&limit=").append(limit);
        }
        
        return executeRequest(Method.GET, urlPath.toString(), authToken, null);
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
