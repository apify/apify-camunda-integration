package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

/**
 * Response object for Get Key-Value Store Record operation.
 * Handles different content types: JSON, text, and binary data.
 */
public record GetKeyValueStoreRecordResponse(
    String contentType,
    JsonNode jsonValue,
    String textValue,
    String base64Value
) implements ApifyResult {}
