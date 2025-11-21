package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

public record RunActorResponse(JsonNode data) implements ApifyResult {

  public RunActorResponse(String jsonResponse) throws Exception {
    this(parseResponse(jsonResponse));
  }

  private static JsonNode parseResponse(String jsonResponse) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(jsonResponse);
    
    // Extract data field if present, otherwise use root
    if (rootNode.has("data")) {
      return rootNode.get("data");
    }
    return rootNode;
  }
}

