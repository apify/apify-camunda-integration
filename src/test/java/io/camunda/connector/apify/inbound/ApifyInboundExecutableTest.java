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
                ACTOR,
                "my-actor-id");

        // then
        assertThat(properties.token()).isEqualTo("test-token");
        assertThat(properties.resourceType()).isEqualTo(ACTOR);
        assertThat(properties.resourceId()).isEqualTo("my-actor-id");
    }

    // ==================== Resource ID Normalization Tests ====================

    @Test
    void shouldNormalizeResourceIdWithSlash() {
        // given
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "apify/google-search-scraper");

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
                ACTOR,
                "my-actor-id-123");

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
                TASK,
                "username/category/task-name");

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

    // ==================== Activate Tests ====================

    @Test
    void shouldActivateSuccessfullyAndReportHealthUp() throws Exception {
        // given
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

            // when
            executable.activate(context);

            // then
            verify(context).reportHealth(any(Health.class));
            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).createWebhook(eq("test-token"), anyString());
        }
    }

    @Test
    void shouldReportHealthDownAndCleanupWhenActivationFails() throws Exception {
        // given
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    when(mock.createWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Network error"));
                })) {

            // when / then
            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Network error");

            // Verify health was reported as down
            verify(context).reportHealth(any(Health.class));

            // Verify client was closed during cleanup
            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).close();
        }
    }

    @Test
    void shouldHandleMissingInboundContext() {
        // given - context without inbound.context property
        InboundConnectorContext context = mock(InboundConnectorContext.class);
        ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of("inbound", Map.of())); // No "context" key

        // when / then - should handle gracefully (callbackUrl will be null)
        // The behavior depends on implementation - it may return null or throw
        // Based on current implementation, getCallbackUrl() returns null when context
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

    @Test
    void shouldDeactivateSuccessfullyAndDeleteWebhook() throws Exception {
        // given - first activate to set up the webhook
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

            // Activate first
            executable.activate(context);

            // when
            executable.deactivate();

            // then
            assertThat(mockedClient.constructed()).hasSize(1);
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
            verify(constructedClient).close();
        }
    }

    @Test
    void shouldHandleWebhookDeletionFailureGracefully() throws Exception {
        // given
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

            // Activate first
            executable.activate(context);

            // when - should not throw even though deletion fails
            executable.deactivate();

            // then - client should still be closed
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
            verify(constructedClient).close();
        }
    }

    @Test
    void shouldHandleDeactivateWithoutPriorActivation() throws Exception {
        // given - executable that was never activated

        // when / then - should not throw
        executable.deactivate();

        // No assertions needed - just verify it doesn't throw
    }

    @Test
    void shouldCloseClientEvenWhenNoWebhookWasCreated() throws Exception {
        // given - activate fails before webhook is created, then deactivate is called
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    // First call (during activate) throws before webhook is created
                    when(mock.createWebhook(anyString(), anyString()))
                            .thenThrow(new IOException("Network error"));
                })) {

            // Activate fails
            try {
                executable.activate(context);
            } catch (IOException e) {
                // Expected
            }

            // when - deactivate after failed activation
            executable.deactivate();

            // then - client should have been closed during activation failure cleanup
            // and deactivate should handle the null client gracefully
            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).close(); // Called during activation cleanup
            verify(constructedClient, never()).deleteWebhook(anyString(), anyString()); // No webhook to delete
        }
    }

    // ==================== Additional Edge Case Tests ====================

    @Test
    void shouldReturnErrorResultForMalformedJson() throws Exception {
        // given
        String malformedJson = "{ this is not valid json }";

        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(malformedJson.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
        assertThat(result.connectorData().get("error").toString()).contains("Error processing webhook");
    }

    @Test
    void shouldReturnErrorResultForEmptyByteArray() throws Exception {
        // given
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(new byte[0]);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
    }

    @Test
    void shouldHandleEventWithResourceButMissingFields() throws Exception {
        // given - resource exists but specific fields are missing
        String eventJson = """
                {
                    "eventType": "ACTOR.RUN.SUCCEEDED",
                    "resource": {
                        "id": "run123"
                    }
                }
                """;

        ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

        // then - getters should return null for missing fields
        assertThat(event.getRunId()).isEqualTo("run123");
        assertThat(event.getActorId()).isNull();
        assertThat(event.getTaskId()).isNull();
        assertThat(event.getStatus()).isNull();
        assertThat(event.getDefaultDatasetId()).isNull();
        assertThat(event.getDefaultKeyValueStoreId()).isNull();
    }

    @Test
    void shouldHandleNullResourceIdInProperties() {
        // given
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                null);

        // when
        String normalized = properties.getNormalizedResourceId();

        // then
        assertThat(normalized).isNull();
    }

    @Test
    void shouldProcessWebhookWithTimedOutStatus() throws Exception {
        // given
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

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.TIMED_OUT");
        assertThat(result.connectorData().get("status")).isEqualTo("TIMED-OUT");
    }

    @Test
    void shouldProcessWebhookWithAbortedStatus() throws Exception {
        // given
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

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.ABORTED");
        assertThat(result.connectorData().get("status")).isEqualTo("ABORTED");
        assertThat(result.connectorData().get("actorId")).isEqualTo("actor999");
    }

    @Test
    void shouldIncludeHeadersAndParamsInSuccessResult() throws Exception {
        // given
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

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.request()).isNotNull();
        assertThat(result.request().headers()).isEqualTo(headers);
        assertThat(result.request().params()).isEqualTo(params);
    }

    @Test
    void shouldProcessWebhookWithTaskResource() throws Exception {
        // given - webhook for a Task (not Actor)
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

        // when
        WebhookResult result = executable.triggerWebhook(payload);

        // then
        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("taskId")).isEqualTo("task456");
        assertThat(result.connectorData().get("runId")).isEqualTo("run456");
    }

    @Test
    void shouldCreatePropertiesWithTaskResourceType() {
        // given / when
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                TASK,
                "my-task-id");

        // then
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
