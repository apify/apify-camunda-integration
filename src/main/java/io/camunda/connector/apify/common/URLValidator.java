package io.camunda.connector.apify.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Validates URL format and blocks private/internal addresses to mitigate SSRF.
 */
public class URLValidator {

    private URLValidator() {}

    /**
     * Validates a URL and throws a descriptive exception if invalid.
     * Rejects private/loopback/link-local addresses to prevent SSRF attacks.
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

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("URL must have a valid host");
            }

            rejectPrivateHost(host);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI syntax: " + e.getMessage(), e);
        }
    }

    /**
     * Rejects hosts that resolve to private, loopback, or link-local addresses.
     */
    private static void rejectPrivateHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                throw new IllegalArgumentException(
                    "URL must not point to a private or loopback address: " + host);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve host: " + host, e);
        }
    }
}
