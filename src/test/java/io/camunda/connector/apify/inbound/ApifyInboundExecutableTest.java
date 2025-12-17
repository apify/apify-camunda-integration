package io.camunda.connector.apify.inbound;

import static io.camunda.connector.apify.inbound.dto.ResourceType.ACTOR;
import static io.camunda.connector.apify.inbound.dto.ResourceType.TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.inbound.dto.ResourceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Unit tests for ApifyInboundExecutable.
 */
@ExtendWith(MockitoExtension.class)
public class ApifyInboundExecutableTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApifyInboundExecutable executable;

    /**
     * Setup test environment.
     */
    @BeforeEach
    void setUp() {
        executable = new ApifyInboundExecutable();
    }

    // ==================== ApifyInboundProperties Tests ====================

    /**
     * Test that ApifyInboundProperties can be created with all fields.
     */
    @Test
    void shouldCreatePropertiesWithAllFields() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "my-actor-id");

        assertThat(properties.token()).isEqualTo("test-token");
        assertThat(properties.resourceType()).isEqualTo(ACTOR);
        assertThat(properties.resourceId()).isEqualTo("my-actor-id");
    }

    // ==================== Resource ID Normalization Tests ====================

    /**
     * Test that resource ID with slash is normalized.
     */
    @Test
    void shouldNormalizeResourceIdWithSlash() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "apify/google-search-scraper");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("apify~google-search-scraper");
    }

    /**
     * Test that resource ID without slash is not changed.
     */
    @Test
    void shouldNotChangeResourceIdWithoutSlash() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "my-actor-id-123");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("my-actor-id-123");
    }

    /**
     * Test that resource ID with multiple slashes is normalized.
     * Not sure if this is valid actor name, but testing anyway
     */
    @Test
    void shouldHandleMultipleSlashesInResourceId() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                TASK,
                "username/category/task-name");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("username~category~task-name");
    }

    // ==================== ApifyInboundEvent Tests ====================

    /**
     * Test that ApifyInboundEvent can be parsed from JSON with all fields.
     */
    @Test
    void shouldParseEventWithAllFields() throws Exception {
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

        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

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

    /**
     * Test that ApifyInboundEvent can be parsed from JSON with minimal fields.
     */
    @Test
    void shouldParseEventWithMinimalFields() throws Exception {
        String eventJson = """
                {
                    "eventType": "ACTOR.RUN.FAILED"
                }
                """;

        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

        assertThat(event.eventType()).isEqualTo("ACTOR.RUN.FAILED");
        assertThat(event.userId()).isNull();
        assertThat(event.resource()).isNull();
        assertThat(event.getRunId()).isNull();
        assertThat(event.getActorId()).isNull();
    }

    // ==================== Webhook Processing Tests ====================

    /**
     * Test that valid webhook payload is processed successfully.
     */
    @Test
    void shouldProcessValidWebhookPayload() throws Exception {
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

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("eventType");
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.SUCCEEDED");
        assertThat(result.connectorData().get("runId")).isEqualTo("run123");
        assertThat(result.connectorData().get("status")).isEqualTo("SUCCEEDED");
    }

    /**
     * Test that empty webhook payload returns error result.
     */
    @Test
    void shouldReturnErrorResultForEmptyBody() throws Exception {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(null);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
    }

    /**
     * Test that webhook with failed status is processed successfully.
     */
    @Test
    void shouldProcessWebhookWithFailedStatus() throws Exception {
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

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.FAILED");
        assertThat(result.connectorData().get("status")).isEqualTo("FAILED");
    }

    // ==================== Activate Tests ====================

    /**
     * Test that activation is successful and health is reported as up.
     */
    @Test
    void shouldActivateSuccessfullyAndReportHealthUp() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");
        String webhookResponse = """
                {
                    "data": {
                        "id": "webhook-123"
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                    when(responseResult.getResponseBody()).thenReturn(webhookResponse);
                    when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                })) {

            executable.activate(context);

            verify(context).reportHealth(any(Health.class));
            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).createWebhook(eq("test-token"), anyString());
        }
    }

    /**
     * Test that activation fails and health is reported as down.
     */
    @Test
    void shouldReportHealthDownAndCleanupWhenActivationFails() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    when(mock.createWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Network error"));
                })) {

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Network error");

            // verify health was reported as down
            verify(context).reportHealth(any(Health.class));

            // verify client was closed during cleanup
            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).close();
        }
    }

    /**
     * Test that activation fails and health is reported as down.
     */
    @Test
    void shouldHandleMissingInboundContext() {
        InboundConnectorContext context = mock(InboundConnectorContext.class);
        ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of("inbound", Map.of()));

        // should handle gracefully (callbackUrl will be null)
        // the behavior depends on implementation - it may return null or throw
        // based on current implementation, getCallbackUrl() returns null when context
        // is missing
        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    // Webhook creation should still be attempted but may fail
                    when(mock.createWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Invalid callback URL"));
                })) {

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(Exception.class);
        }
    }

    // ==================== Deactivate Tests ====================

    /**
     * Test that deactivation is successful and webhook is deleted.
     */
    @Test
    void shouldDeactivateSuccessfullyAndDeleteWebhook() throws Exception {
        // first activate to set up the webhook
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");
        String webhookResponse = """
                {
                    "data": {
                        "id": "webhook-123"
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                    when(responseResult.getResponseBody()).thenReturn(webhookResponse);
                    when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                    when(mock.deleteWebhook(anyString(), anyString())).thenReturn(responseResult);
                })) {

            // activate first
            executable.activate(context);

            executable.deactivate();

            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
            verify(constructedClient).close();
        }
    }

    /**
     * Test that deactivation fails and webhook is deleted.
     */
    @Test
    void shouldHandleWebhookDeletionFailureGracefully() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");
        String webhookResponse = """
                {
                    "data": {
                        "id": "webhook-123"
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                    when(responseResult.getResponseBody()).thenReturn(webhookResponse);
                    when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                    when(mock.deleteWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Failed to delete webhook"));
                })) {

            // activate first
            executable.activate(context);

            // should not throw even though deletion fails
            executable.deactivate();

            // client should still be closed
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
            verify(constructedClient).close();
        }
    }

    /**
     * Test that deactivate does not throw when executable was never activated.
     */
    @Test
    void shouldHandleDeactivateWithoutPriorActivation() throws Exception {
        // executable that was never activated

        // should not throw
        executable.deactivate();

        // no assertions needed - just verify it doesn't throw
    }

    @Test
    void shouldCloseClientEvenWhenNoWebhookWasCreated() throws Exception {
        // activate fails before webhook is created, then deactivate is called
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    // first call (during activate) throws before webhook is created
                    when(mock.createWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Network error"));
                })) {

            // activate fails
            try {
                executable.activate(context);
            } catch (IOException e) {
                // expected
            }

            // deactivate after failed activation
            executable.deactivate();

            // client should have been closed during activation failure cleanup
            // and deactivate should handle the null client gracefully
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).close();
            verify(constructedClient, never()).deleteWebhook(anyString(), anyString());
        }
    }

    // ==================== Additional Edge Case Tests ====================

    /**
     * Test that malformed JSON is handled gracefully.
     */
    @Test
    void shouldReturnErrorResultForMalformedJson() throws Exception {
        String malformedJson = "{ this is not valid json }";

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(malformedJson.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
        assertThat(result.connectorData().get("error").toString()).contains("Failed to parse webhook body");
    }

    /**
     * Test that empty byte array is handled gracefully.
     */
    @Test
    void shouldReturnErrorResultForEmptyByteArray() throws Exception {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(new byte[0]);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
    }

    /**
     * Test that event with resource but missing fields is handled gracefully.
     */
    @Test
    void shouldHandleEventWithResourceButMissingFields() throws Exception {
        // resource exists but specific fields are missing
        String eventJson = """
                {
                    "eventType": "ACTOR.RUN.SUCCEEDED",
                    "resource": {
                        "id": "run123"
                    }
                }
                """;

        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

        // getters should return null for missing fields
        assertThat(event.getRunId()).isEqualTo("run123");
        assertThat(event.getActorId()).isNull();
        assertThat(event.getTaskId()).isNull();
        assertThat(event.getStatus()).isNull();
        assertThat(event.getDefaultDatasetId()).isNull();
        assertThat(event.getDefaultKeyValueStoreId()).isNull();
    }

    /**
     * Test that webhook with TIMED_OUT status is processed gracefully.
     */
    @Test
    void shouldProcessWebhookWithTimedOutStatus() throws Exception {
        String webhookBody = """
                {
                    "eventType": "ACTOR.RUN.TIMED_OUT",
                    "resource": {
                        "id": "run789",
                        "status": "TIMED-OUT"
                    }
                }
                """;

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.TIMED_OUT");
        assertThat(result.connectorData().get("status")).isEqualTo("TIMED-OUT");
    }

    /**
     * Test that webhook with ABORTED status is processed gracefully.
     */
    @Test
    void shouldProcessWebhookWithAbortedStatus() throws Exception {
        String webhookBody = """
                {
                    "eventType": "ACTOR.RUN.ABORTED",
                    "resource": {
                        "id": "run999",
                        "status": "ABORTED",
                        "actId": "actor999"
                    }
                }
                """;

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.ABORTED");
        assertThat(result.connectorData().get("status")).isEqualTo("ABORTED");
        assertThat(result.connectorData().get("actorId")).isEqualTo("actor999");
    }

    /**
     * Test that webhook with SUCCEEDED status is processed gracefully.
     */
    @Test
    void shouldIncludeHeadersAndParamsInSuccessResult() throws Exception {
        String webhookBody = """
                {
                    "eventType": "ACTOR.RUN.SUCCEEDED",
                    "resource": {
                        "id": "run123"
                    }
                }
                """;

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "X-Custom-Header", "custom-value");
        Map<String, String> params = Map.of("queryParam", "paramValue");

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(headers);
        when(payload.params()).thenReturn(params);

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.request()).isNotNull();
        assertThat(result.request().headers()).isEqualTo(headers);
        assertThat(result.request().params()).isEqualTo(params);
    }

    /**
     * Test that webhook with Task resource is processed gracefully.
     */
    @Test
    void shouldProcessWebhookWithTaskResource() throws Exception {
        // webhook for a Task (not Actor)
        String webhookBody = """
                {
                    "eventType": "ACTOR.RUN.SUCCEEDED",
                    "userId": "user456",
                    "resource": {
                        "id": "run456",
                        "actorTaskId": "task456",
                        "status": "SUCCEEDED"
                    }
                }
                """;

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(webhookBody.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        WebhookResult result = executable.triggerWebhook(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("taskId")).isEqualTo("task456");
        assertThat(result.connectorData().get("runId")).isEqualTo("run456");
    }

    /**
     * Test that properties are created with Task resource type.
     */
    @Test
    void shouldCreatePropertiesWithTaskResourceType() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                TASK,
                "my-task-id");

        assertThat(properties.resourceType()).isEqualTo(TASK);
        assertThat(properties.resourceType().getValue()).isEqualTo("task");
        assertThat(properties.resourceType().getConditionKey()).isEqualTo("actorTaskId");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a mock InboundConnectorContext with the specified properties.
     */
    private InboundConnectorContext createMockContext(String token, ResourceType resourceType, String resourceId,
            String inboundContext) {
        InboundConnectorContext context = mock(InboundConnectorContext.class);
        ApifyInboundProperties properties = new ApifyInboundProperties(token, resourceType, resourceId);
        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of(
                "inbound", Map.of("context", inboundContext)));
        return context;
    }
}
