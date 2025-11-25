package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.apify.outbound.ApifyResult;

/**
 * Response object for Get Key-Value Store Record operation.
 * Handles different content types: JSON, text, and binary data.
 */
public class GetKeyValueStoreRecordResponse implements ApifyResult {
    
    /**
     * Content-Type header returned by Apify.
     * Tells the client how to interpret the data.
     */
    @JsonProperty("contentType")
    private String contentType;

    /**
     * JSON value if the record is application/json.
     * Will be null if the record is not JSON.
     */
    @JsonProperty("jsonValue")
    private JsonNode jsonValue;

    /**
     * Text value if the record is text/plain, text/html, etc.
     * Will be null if the record is not text-based.
     */
    @JsonProperty("textValue")
    private String textValue;

    /**
     * Raw bytes if the record is binary (e.g. image, zip).
     * Will be null if the record is JSON or text.
     * Stored as base64 encoded string for JSON serialization.
     */
    @JsonProperty("base64Value")
    private String base64Value;

    // Constructors
    public GetKeyValueStoreRecordResponse() {
    }

    public GetKeyValueStoreRecordResponse(String contentType, JsonNode jsonValue, String textValue, String base64Value) {
        this.contentType = contentType;
        this.jsonValue = jsonValue;
        this.textValue = textValue;
        this.base64Value = base64Value;
    }

    // Getters and setters
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public JsonNode getJsonValue() {
        return jsonValue;
    }

    public void setJsonValue(JsonNode jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public String getBase64Value() {
        return base64Value;
    }

    public void setBase64Value(String base64Value) {
        this.base64Value = base64Value;
    }
}

