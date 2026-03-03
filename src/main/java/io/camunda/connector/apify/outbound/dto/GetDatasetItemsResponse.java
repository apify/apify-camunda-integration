package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

import java.util.List;

public record GetDatasetItemsResponse(List<JsonNode> items) implements ApifyResult {

  public GetDatasetItemsResponse(String jsonResponse) throws Exception {
    this(parseItems(jsonResponse));
  }

  private static List<JsonNode> parseItems(String jsonResponse) throws Exception {
    JsonNode rootNode = SHARED_OBJECT_MAPPER.readTree(jsonResponse);
    if (rootNode.isArray()) {
      return SHARED_OBJECT_MAPPER.convertValue(rootNode, new TypeReference<>() {});
    }
    return List.of(rootNode);
  }
}
