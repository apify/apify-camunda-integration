package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

/**
 * Response object for Get Key-Value Store Record operation.
 * Handles different content types: JSON, text, and binary data.
 */
public record GetKeyValueStoreRecordResponse(
    @JsonProperty("contentType") String contentType,
    @JsonProperty("jsonValue") JsonNode jsonValue,
    @JsonProperty("textValue") String textValue,
    @JsonProperty("base64Value") String base64Value
) implements ApifyResult {}
