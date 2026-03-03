package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

/**
 * Response for Get Key-Value Store Record operation.
 * Handles JSON, text, and binary (base64-encoded) content types.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetKeyValueStoreRecordResponse(
    @JsonProperty("contentType") String contentType,
    @JsonProperty("jsonValue") JsonNode jsonValue,
    @JsonProperty("textValue") String textValue,
    @JsonProperty("base64Value") String base64Value
) implements ApifyResult {}
