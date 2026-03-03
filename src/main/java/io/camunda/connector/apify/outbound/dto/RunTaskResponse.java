package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

public record RunTaskResponse(JsonNode data) implements ApifyResult {

  public RunTaskResponse(String jsonResponse) throws Exception {
    this(parseResponse(jsonResponse));
  }

  private static JsonNode parseResponse(String jsonResponse) throws Exception {
    JsonNode rootNode = SHARED_OBJECT_MAPPER.readTree(jsonResponse);
    if (rootNode.has("data")) {
      return rootNode.get("data");
    }
    return rootNode;
  }
}
