package io.camunda.connector.apify.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents the webhook payload received from Apify when an Actor or Task run
 * completes.
 * 
 * This DTO maps the JSON payload sent by Apify webhooks to Java fields.
 * The payload contains information about the event type and the resource (run)
 * that triggered it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApifyInboundEvent(

        /**
         * The type of event that triggered the webhook.
         * Examples: "ACTOR.RUN.SUCCEEDED", "ACTOR.RUN.FAILED", "ACTOR.RUN.TIMED_OUT",
         * "ACTOR.RUN.ABORTED"
         */
        @JsonProperty("eventType") String eventType,

        /**
         * The user ID of the Apify user who owns the Actor/Task.
         */
        @JsonProperty("userId") String userId,

        /**
         * The timestamp when the event was created.
         */
        @JsonProperty("createdAt") String createdAt,

        /**
         * The resource data containing details about the run.
         * This includes fields like id, actId, status, startedAt, finishedAt,
         * defaultDatasetId, defaultKeyValueStoreId, etc.
         */
        @JsonProperty("resource") JsonNode resource,

        /**
         * Additional event data/metadata sent by Apify.
         */
        @JsonProperty("eventData") JsonNode eventData) {
    /**
     * Extracts the run ID from the resource.
     * 
     * @return The run ID, or null if not present
     */
    public String getRunId() {
        return getResourceField("id");
    }

    /**
     * Extracts the actor ID from the resource.
     * 
     * @return The actor ID, or null if not present
     */
    public String getActorId() {
        return getResourceField("actId");
    }

    /**
     * Extracts the task ID from the resource.
     * 
     * @return The task ID, or null if not present
     */
    public String getTaskId() {
        return getResourceField("actorTaskId");
    }

    /**
     * Extracts the run status from the resource.
     * 
     * @return The run status, or null if not present
     */
    public String getStatus() {
        return getResourceField("status");
    }

    /**
     * Extracts the default dataset ID from the resource.
     * 
     * @return The default dataset ID, or null if not present
     */
    public String getDefaultDatasetId() {
        return getResourceField("defaultDatasetId");
    }

    /**
     * Extracts the default key-value store ID from the resource.
     * 
     * @return The default key-value store ID, or null if not present
     */
    public String getDefaultKeyValueStoreId() {
        return getResourceField("defaultKeyValueStoreId");
    }

    /**
     * Extracts a field from the resource.
     * 
     * @param fieldName The name of the field to extract.
     * @return The field value, or null if not present
     */
    private String getResourceField(String fieldName) {
        return resource != null && resource.has(fieldName) ? resource.get(fieldName).asText() : null;
    }
}
