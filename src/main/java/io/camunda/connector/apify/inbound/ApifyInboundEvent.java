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
    // TODO: check if it's all necessary data, compare it with power automate for
    // instance
    /**
     * Extracts the run ID from the resource.
     * 
     * @return The run ID, or null if not present
     */
    public String getRunId() {
        if (resource != null && resource.has("id")) {
            return resource.get("id").asText();
        }
        return null;
    }

    /**
     * Extracts the actor ID from the resource.
     * 
     * @return The actor ID, or null if not present
     */
    public String getActorId() {
        if (resource != null && resource.has("actId")) {
            return resource.get("actId").asText();
        }
        return null;
    }

    /**
     * Extracts the task ID from the resource.
     * 
     * @return The task ID, or null if not present
     */
    public String getTaskId() {
        if (resource != null && resource.has("actorTaskId")) {
            return resource.get("actorTaskId").asText();
        }
        return null;
    }

    /**
     * Extracts the run status from the resource.
     * 
     * @return The run status, or null if not present
     */
    public String getStatus() {
        if (resource != null && resource.has("status")) {
            return resource.get("status").asText();
        }
        return null;
    }

    /**
     * Extracts the default dataset ID from the resource.
     * 
     * @return The default dataset ID, or null if not present
     */
    public String getDefaultDatasetId() {
        if (resource != null && resource.has("defaultDatasetId")) {
            return resource.get("defaultDatasetId").asText();
        }
        return null;
    }

    /**
     * Extracts the default key-value store ID from the resource.
     * 
     * @return The default key-value store ID, or null if not present
     */
    public String getDefaultKeyValueStoreId() {
        if (resource != null && resource.has("defaultKeyValueStoreId")) {
            return resource.get("defaultKeyValueStoreId").asText();
        }
        return null;
    }
}
