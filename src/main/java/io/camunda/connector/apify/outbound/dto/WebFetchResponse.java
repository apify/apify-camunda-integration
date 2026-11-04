package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

/**
 * The Web Fetch envelope as returned by the Actor's Standby endpoint: {@code url}, {@code fetch},
 * {@code metadata} and one field per requested format. Unlike Apify API responses, it is not
 * wrapped in a {@code data} field.
 */
public record WebFetchResponse(JsonNode data) implements ApifyResult {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public WebFetchResponse(String jsonResponse) throws Exception {
    this(OBJECT_MAPPER.readTree(jsonResponse));
  }
}
