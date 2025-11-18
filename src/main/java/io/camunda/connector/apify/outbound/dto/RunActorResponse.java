package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

public record RunActorResponse(JsonNode data) implements ApifyResult {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public RunActorResponse(String jsonResponse) {
    this(parseResponse(jsonResponse));
  }

  private static JsonNode parseResponse(String jsonResponse) {
    try {
      JsonNode rootNode = objectMapper.readTree(jsonResponse);
      
      // Extract data field if present, otherwise use root
      if (rootNode.has("data")) {
        return rootNode.get("data");
      }
      return rootNode;
    } catch (Exception e) {
      // If parsing fails, return empty object
      return objectMapper.createObjectNode();
    }
  }
}

