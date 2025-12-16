package io.camunda.connector.apify.inbound.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.apify.inbound.ApifyInboundEvent;

import java.util.Map;

/**
 * DTO representing the connector data passed to the Camunda process
 * when an Apify webhook event is received.
 * 
 * This provides a type-safe, well-documented structure for the data
 * that flows into Camunda process variables.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApifyWebhookResponse(
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
         * The unique identifier of the Actor/Task run.
         */
        @JsonProperty("runId") String runId,

        /**
         * The status of the run (e.g., "SUCCEEDED", "FAILED").
         */
        @JsonProperty("status") String status,

        /**
         * The unique identifier of the Actor.
         */
        @JsonProperty("actorId") String actorId,

        /**
         * The unique identifier of the Task (if applicable).
         */
        @JsonProperty("taskId") String taskId,

        /**
         * The default dataset ID where run results are stored.
         */
        @JsonProperty("defaultDatasetId") String defaultDatasetId,

        /**
         * The default key-value store ID for the run.
         */
        @JsonProperty("defaultKeyValueStoreId") String defaultKeyValueStoreId,

        /**
         * The full resource object containing all run details.
         */
        @JsonProperty("resource") Map<String, Object> resource,

        /**
         * Additional event data/metadata sent by Apify.
         */
        @JsonProperty("eventData") Map<String, Object> eventData) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates connector data from an ApifyInboundEvent.
     * 
     * @param event  The inbound event received from Apify webhook.
     * @param mapper The ObjectMapper to use for converting JsonNode to Map.
     * @return A new ApifyInboundConnectorData instance with extracted data.
     */
    public static ApifyWebhookResponse fromEvent(ApifyInboundEvent event) {
        return new ApifyWebhookResponse(
                event.eventType(),
                event.userId(),
                event.createdAt(),
                event.getRunId(),
                event.getStatus(),
                event.getActorId(),
                event.getTaskId(),
                event.getDefaultDatasetId(),
                event.getDefaultKeyValueStoreId(),
                convertJsonNode(event.resource()),
                convertJsonNode(event.eventData()));
    }

    /**
     * Converts a JsonNode to a Map, returning null if the node is null or empty.
     */
    private static Map<String, Object> convertJsonNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(node, MAP_TYPE_REF);
    }
}
