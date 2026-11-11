package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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

  // ApifyFunction logs the request input, and header values often carry credentials for the target site.
  @Override
  public String toString() {
    return "WebFetchInput[url=" + url + ", formats=" + formats + ", headers=" + maskedHeaders() + "]";
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
