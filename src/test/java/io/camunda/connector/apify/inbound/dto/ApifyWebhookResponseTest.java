package io.camunda.connector.apify.inbound.dto;

import static io.camunda.connector.apify.inbound.InboundTestFixtures.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.apify.inbound.ApifyInboundEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for ApifyWebhookResponse.
 */
class ApifyWebhookResponseTest {

    private ObjectNode resourceNode;
    private ObjectNode eventDataNode;

    @BeforeEach
    void setUp() {
        resourceNode = OBJECT_MAPPER.createObjectNode();
        eventDataNode = OBJECT_MAPPER.createObjectNode();
    }

    @Nested
    @DisplayName("fromEvent() Conversion")
    class FromEventConversion {

        @Test
        void shouldConvertEventWithAllFields() throws Exception {
            resourceNode.put("id", "run123");
            resourceNode.put("actId", "actor123");
            resourceNode.put("actorTaskId", "task123");
            resourceNode.put("status", "SUCCEEDED");
            resourceNode.put("defaultDatasetId", "dataset123");
            resourceNode.put("defaultKeyValueStoreId", "kvstore123");

            eventDataNode.put("someKey", "someValue");

            String eventJson = String.format("""
                    {
                        "eventType": "ACTOR.RUN.SUCCEEDED",
                        "userId": "user123",
                        "createdAt": "2024-01-15T10:30:00.000Z",
                        "resource": %s,
                        "eventData": %s
                    }
                    """, resourceNode.toString(), eventDataNode.toString());

            ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

            ApifyWebhookResponse response = ApifyWebhookResponse.fromEvent(event);

            assertThat(response.eventType()).isEqualTo("ACTOR.RUN.SUCCEEDED");
            assertThat(response.userId()).isEqualTo("user123");
            assertThat(response.createdAt()).isEqualTo("2024-01-15T10:30:00.000Z");
            assertThat(response.runId()).isEqualTo("run123");
            assertThat(response.actorId()).isEqualTo("actor123");
            assertThat(response.taskId()).isEqualTo("task123");
            assertThat(response.status()).isEqualTo("SUCCEEDED");
            assertThat(response.defaultDatasetId()).isEqualTo("dataset123");
            assertThat(response.defaultKeyValueStoreId()).isEqualTo("kvstore123");
            assertThat(response.resource()).isNotNull();
            assertThat(response.resource()).containsEntry("id", "run123");
            assertThat(response.eventData()).isNotNull();
            assertThat(response.eventData()).containsEntry("someKey", "someValue");
        }

        @Test
        void shouldConvertEventWithMinimalFields() throws Exception {
            String eventJson = """
                    {
                        "eventType": "ACTOR.RUN.FAILED"
                    }
                    """;

            ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

            ApifyWebhookResponse response = ApifyWebhookResponse.fromEvent(event);

            assertThat(response.eventType()).isEqualTo("ACTOR.RUN.FAILED");
            assertThat(response.userId()).isNull();
            assertThat(response.createdAt()).isNull();
            assertThat(response.runId()).isNull();
            assertThat(response.actorId()).isNull();
            assertThat(response.taskId()).isNull();
            assertThat(response.status()).isNull();
            assertThat(response.defaultDatasetId()).isNull();
            assertThat(response.defaultKeyValueStoreId()).isNull();
            assertThat(response.resource()).isNull();
            assertThat(response.eventData()).isNull();
        }

        @Test
        void shouldHandleEmptyResourceObject() throws Exception {
            String eventJson = """
                    {
                        "eventType": "ACTOR.RUN.SUCCEEDED",
                        "resource": {}
                    }
                    """;

            ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

            ApifyWebhookResponse response = ApifyWebhookResponse.fromEvent(event);

            assertThat(response.eventType()).isEqualTo("ACTOR.RUN.SUCCEEDED");
            assertThat(response.runId()).isNull();
            assertThat(response.resource()).isNull();
        }

        @Test
        void shouldConvertComplexResourceObject() throws Exception {
            resourceNode.put("id", "run123");
            resourceNode.put("actId", "actor123");
            resourceNode.put("status", "SUCCEEDED");
            ObjectNode nestedNode = OBJECT_MAPPER.createObjectNode();
            nestedNode.put("nestedKey", "nestedValue");
            resourceNode.set("nestedObject", nestedNode);

            String eventJson = String.format("""
                    {
                        "eventType": "ACTOR.RUN.SUCCEEDED",
                        "resource": %s
                    }
                    """, resourceNode.toString());

            ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

            ApifyWebhookResponse response = ApifyWebhookResponse.fromEvent(event);

            assertThat(response.resource()).isNotNull();
            assertThat(response.resource()).containsKey("nestedObject");
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) response.resource().get("nestedObject");
            assertThat(nested).containsEntry("nestedKey", "nestedValue");
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {
        @Test
        void shouldSerializeToJsonCorrectly() throws Exception {
            ApifyWebhookResponse response = new ApifyWebhookResponse(
                    "ACTOR.RUN.SUCCEEDED",
                    "user123",
                    "2024-01-15T10:30:00.000Z",
                    "run123",
                    "SUCCEEDED",
                    "actor123",
                    null, // taskId
                    "dataset123",
                    "kvstore123",
                    Map.of("id", "run123"),
                    null // eventData
            );

            String json = OBJECT_MAPPER.writeValueAsString(response);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            assertThat(jsonNode.has("eventType")).isTrue();
            assertThat(jsonNode.get("eventType").asText()).isEqualTo("ACTOR.RUN.SUCCEEDED");
            assertThat(jsonNode.has("taskId")).isFalse();
            assertThat(jsonNode.has("eventData")).isFalse();
        }

        @Test
        void shouldExcludeNullFieldsInSerialization() throws Exception {
            ApifyWebhookResponse response = new ApifyWebhookResponse(
                    "ACTOR.RUN.FAILED",
                    null,
                    null,
                    "run123",
                    "FAILED",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            String json = OBJECT_MAPPER.writeValueAsString(response);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(json);

            assertThat(jsonNode.has("eventType")).isTrue();
            assertThat(jsonNode.has("runId")).isTrue();
            assertThat(jsonNode.has("status")).isTrue();
            assertThat(jsonNode.has("userId")).isFalse();
            assertThat(jsonNode.has("createdAt")).isFalse();
            assertThat(jsonNode.has("actorId")).isFalse();
        }
    }
}
