package io.camunda.connector.apify.common;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates URLs format.
 */
public class URLValidator {
    
    private URLValidator() {}
    
    /**
     * Validates a URL and throws a descriptive exception if invalid.
     * Useful when you want to provide detailed error messages.
     * 
     * @param urlString The URL to validate.
     * @throws IllegalArgumentException with a descriptive message if the URL is invalid.
     */
    public static void validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        try {
            URI uri = new URI(urlString);
            
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("URL must be absolute (include scheme like http:// or https://)");
            }
            
            String scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException(
                    "Invalid protocol '" + scheme + "'. Only HTTP and HTTPS are supported.");
            }
            
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("URL must have a valid host");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI syntax: " + e.getMessage(), e);
        }
    }
}
