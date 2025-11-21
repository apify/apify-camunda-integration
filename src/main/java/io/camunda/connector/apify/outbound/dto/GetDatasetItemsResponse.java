package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

import java.util.List;
import java.util.ArrayList;

public record GetDatasetItemsResponse(List<JsonNode> items) implements ApifyResult {

  public GetDatasetItemsResponse(String jsonResponse) throws Exception {
    this(parseItems(jsonResponse));
  }

  private static List<JsonNode> parseItems(String jsonResponse) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(jsonResponse);
    
    if (rootNode.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      for (JsonNode item : rootNode) {
        items.add(item);
      }
      return items;
    } else {
      // If it's a single object, wrap it in a list
      return List.of(rootNode);
    }
  }
}

