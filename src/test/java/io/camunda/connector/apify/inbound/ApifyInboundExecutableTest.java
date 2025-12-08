package io.camunda.connector.apify.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Unit tests for ApifyInboundExecutable.
 */
@ExtendWith(MockitoExtension.class)
public class ApifyInboundExecutableTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApifyInboundExecutable executable;

    @BeforeEach
    void setUp() {
        executable = new ApifyInboundExecutable();
    }



    // ==================== ApifyInboundProperties Tests ====================

    @Test
    void shouldCreatePropertiesWithAllFields() {
        // given / when
        ApifyInboundProperties properties = new ApifyInboundProperties(
            "test-token",
            "actor",
            "my-actor-id"
        );

        // then
        assertThat(properties.token()).isEqualTo("test-token");
        assertThat(properties.resourceType()).isEqualTo("actor");
        assertThat(properties.resourceId()).isEqualTo("my-actor-id");
    }
    
    // ==================== Resource ID Normalization Tests ====================

    @Test
    void shouldNormalizeResourceIdWithSlash() {
        // given
        ApifyInboundProperties properties = new ApifyInboundProperties(
            "test-token",
            "actor",
            "apify/google-search-scraper"
        );

        // when
        String normalized = properties.getNormalizedResourceId();

        // then
        assertThat(normalized).isEqualTo("apify~google-search-scraper");
    }

    @Test
    void shouldNotChangeResourceIdWithoutSlash() {
        // given
        ApifyInboundProperties properties = new ApifyInboundProperties(
            "test-token",
            "actor",
            "my-actor-id-123"
        );

        // when
        String normalized = properties.getNormalizedResourceId();

        // then
        assertThat(normalized).isEqualTo("my-actor-id-123");
    }

    @Test
    void shouldHandleMultipleSlashesInResourceId() {
        // given
        ApifyInboundProperties properties = new ApifyInboundProperties(
            "test-token",
            "task",
            "username/category/task-name"
        );

        // when
        String normalized = properties.getNormalizedResourceId();

        // then
        assertThat(normalized).isEqualTo("username~category~task-name");
    }

    // ==================== ApifyInboundEvent Tests ====================

    @Test
    void shouldParseEventWithAllFields() throws Exception {
        // given
        String eventJson = """
            {
                "eventType": "ACTOR.RUN.SUCCEEDED",
                "userId": "user123",
                "createdAt": "2024-01-15T10:30:00.000Z",
                "resource": {
                    "id": "run123",
                    "actId": "actor123",
                    "actorTaskId": "task123",
                    "status": "SUCCEEDED",
                    "defaultDatasetId": "dataset123",
                    "defaultKeyValueStoreId": "kvstore123"
                },
                "eventData": {
                    "someKey": "someValue"
                }
            }
            """;

        // when
        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

        // then
        assertThat(event.eventType()).isEqualTo("ACTOR.RUN.SUCCEEDED");
        assertThat(event.userId()).isEqualTo("user123");
        assertThat(event.createdAt()).isEqualTo("2024-01-15T10:30:00.000Z");
        assertThat(event.getRunId()).isEqualTo("run123");
        assertThat(event.getActorId()).isEqualTo("actor123");
        assertThat(event.getTaskId()).isEqualTo("task123");
        assertThat(event.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(event.getDefaultDatasetId()).isEqualTo("dataset123");
        assertThat(event.getDefaultKeyValueStoreId()).isEqualTo("kvstore123");
    }

    @Test
    void shouldParseEventWithMinimalFields() throws Exception {
        // given
        String eventJson = """
            {
                "eventType": "ACTOR.RUN.FAILED"
            }
            """;

        // when
        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

        // then
        assertThat(event.eventType()).isEqualTo("ACTOR.RUN.FAILED");
        assertThat(event.userId()).isNull();
        assertThat(event.resource()).isNull();
        assertThat(event.getRunId()).isNull();
        assertThat(event.getActorId()).isNull();
    }

    // ==================== Webhook Processing Tests ====================

    @Test
    void shouldProcessValidWebhookPayload() throws Exception {
        // given
        String webhookBody = """
            {
                "eventType": "ACTOR.RUN.SUCCEEDED",
                "userId": "user123",
                "createdAt": "2024-01-15T10:30:00.000Z",
                "resource": {
                    "id": "run123",
                    "actId": "actor123",
                    "status": "SUCCEEDED",
                    "defaultDatasetId": "dataset123"
                }
            }
            """;
        
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of("Content-Type", "application/json"));
        when(payload.params()).thenReturn(Map.of());

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("eventType");
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.SUCCEEDED");
        assertThat(result.connectorData().get("runId")).isEqualTo("run123");
        assertThat(result.connectorData().get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldReturnErrorResultForEmptyBody() throws Exception {
        // given
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(null);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
    }

    @Test
    void shouldProcessWebhookWithFailedStatus() throws Exception {
        // given
        String webhookBody = """
            {
                "eventType": "ACTOR.RUN.FAILED",
                "resource": {
                    "id": "run456",
                    "status": "FAILED"
                }
            }
            """;
        
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.FAILED");
        assertThat(result.connectorData().get("status")).isEqualTo("FAILED");
    }
}
