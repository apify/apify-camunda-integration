package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public record WebFetchInput(
    @NotEmpty
    @Pattern(regexp = "^\\s*https?://.*$", message = "URL must start with http:// or https://")
    String url,

    // Comma-separated subset of markdown, html, text, links, raw. Blank lets Web Fetch pick from the content type.
    String formats,

    // HTTP headers that Web Fetch sends to the target site, as a JSON object.
    JsonNode headers
) {

  // ApifyFunction logs the request input, and URL query strings, URL user info and header values
  // often carry credentials for the target site.
  @Override
  public String toString() {
    return "WebFetchInput[url=" + maskedUrl() + ", formats=" + formats + ", headers=" + maskedHeaders() + "]";
  }

  private String maskedUrl() {
    if (url == null) {
      return "null";
    }
    try {
      URI uri = new URI(url.trim());
      StringBuilder masked = new StringBuilder();
      if (uri.getScheme() != null) {
        masked.append(uri.getScheme()).append("://");
      }
      if (uri.getRawUserInfo() != null) {
        masked.append("***@");
      }
      if (uri.getHost() != null) {
        masked.append(uri.getHost());
      }
      if (uri.getPort() != -1) {
        masked.append(':').append(uri.getPort());
      }
      if (uri.getRawPath() != null) {
        masked.append(uri.getRawPath());
      }
      if (uri.getRawQuery() != null) {
        masked.append("?***");
      }
      return masked.toString();
    } catch (URISyntaxException e) {
      return "***";
    }
  }

  private String maskedHeaders() {
    if (headers == null || headers.isNull()) {
      return "null";
    }
    if (!headers.isObject()) {
      return "***";
    }
    List<String> names = new ArrayList<>();
    headers.fieldNames().forEachRemaining(name -> names.add(name + "=***"));
    return "{" + String.join(", ", names) + "}";
  }
}
