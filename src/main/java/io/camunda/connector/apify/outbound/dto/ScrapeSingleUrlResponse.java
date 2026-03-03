package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

public record ScrapeSingleUrlResponse(JsonNode data) implements ApifyResult {

  public ScrapeSingleUrlResponse(String jsonResponse) throws Exception {
    this(SHARED_OBJECT_MAPPER.readTree(jsonResponse));
  }
}
