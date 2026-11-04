package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record WebFetchInput(
    @NotEmpty
    @Pattern(regexp = "^\\s*https?://.*$", message = "URL must start with http:// or https://")
    String url,

    // Comma-separated subset of markdown, html, text, links, raw. Blank lets Web Fetch pick from the content type.
    String formats,

    // HTTP headers that Web Fetch sends to the target site, as a JSON object.
    JsonNode headers
) {}
