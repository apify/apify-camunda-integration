package io.camunda.connector.apify.common;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates URL format and blocks private/internal addresses to mitigate SSRF.
 *
 * <p><strong>SSRF mitigation:</strong> This class blocks obvious private/local addresses via DNS at validation,
 * but cannot prevent DNS rebinding—attackers could later point the hostname to a private IP.
 * For full protection, repeat address checks before the actual HTTP request.
 */
public class URLValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(URLValidator.class);

    private URLValidator() {}

    /**
     * Validates a URL and throws a descriptive exception if invalid.
     * Rejects private/loopback/link-local addresses to prevent SSRF attacks.
     *
     * <p>If the host cannot be resolved due to a transient DNS failure, the URL is allowed through
     * and a warning is logged. Unresolvable hosts that are genuinely unreachable will fail at
     * request time; blocking them here would cause false positives under DNS instability.
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
     * Rejects hosts resolving to private, loopback, or link-local addresses to prevent SSRF.
     * Allows unresolved hosts with a warning. Does not prevent DNS rebinding.
     * @param host The hostname or IP address to check.
     * @throws IllegalArgumentException if the host resolves to a private or internal address.
     */
    private static void rejectPrivateHost(String host) {
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            LOGGER.warn("Could not resolve host '{}' during URL validation; allowing request to proceed. " +
                "If the host is unreachable it will fail at connection time.", host);
            return;
        }

        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
            throw new IllegalArgumentException(
                "URL must not point to a private or loopback address: " + host);
        }
    }
}
