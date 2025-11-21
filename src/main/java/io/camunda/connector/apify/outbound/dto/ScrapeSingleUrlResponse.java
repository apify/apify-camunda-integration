package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.outbound.ApifyResult;

public record ScrapeSingleUrlResponse(JsonNode data) implements ApifyResult {

  public ScrapeSingleUrlResponse(String jsonResponse) throws Exception {
    this(parseResponse(jsonResponse));
  }

  private static JsonNode parseResponse(String jsonResponse) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(jsonResponse);
      
    return rootNode;
  }
}

