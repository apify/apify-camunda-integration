package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

import java.util.List;

public record GetDatasetItemsResponse(List<JsonNode> items) implements ApifyResult {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public GetDatasetItemsResponse(String jsonResponse) {
    this(parseItems(jsonResponse));
  }

  private static List<JsonNode> parseItems(String jsonResponse) {
    try {
      JsonNode rootNode = objectMapper.readTree(jsonResponse);
      
      if (rootNode.isArray()) {
        List<JsonNode> items = new java.util.ArrayList<>();
        for (JsonNode item : rootNode) {
          items.add(item);
        }
        return items;
      } else {
        // If it's a single object, wrap it in a list
        return List.of(rootNode);
      }
    } catch (Exception e) {
      // If parsing fails, return empty list
      return List.of();
    }
  }
}

