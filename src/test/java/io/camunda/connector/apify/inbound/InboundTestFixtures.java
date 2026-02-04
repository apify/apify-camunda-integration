package io.camunda.connector.apify.inbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.inbound.dto.ResourceType;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared test fixtures and utilities for inbound connector tests.
 */
public final class InboundTestFixtures {

    /**
     * Shared ObjectMapper instance for all tests.
     */
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Valid webhook creation response from Apify API.
     */
    public static final String VALID_WEBHOOK_RESPONSE = """
            {"data":{"id":"webhook-123"}}
            """;

    /**
     * Empty webhooks list response from Apify API.
     */
    public static final String EMPTY_WEBHOOKS_LIST = """
            {"data":{"items":[]}}
            """;

    /**
     * Private constructor to prevent instantiation.
     */
    private InboundTestFixtures() {
    }

    /**
     * Creates a webhook list response JSON with a single webhook.
     *
     * @param webhookId  The webhook ID.
     * @param requestUrl The webhook callback URL.
     * @return The JSON response string.
     */
    public static String webhookListResponse(String webhookId, String requestUrl) {
        return """
                {
                    "data": {
                        "items": [
                            {
                                "id": "%s",
                                "requestUrl": "%s"
                            }
                        ]
                    }
                }
                """.formatted(webhookId, requestUrl);
    }

    /**
     * Creates a mock InboundConnectorContext with the specified properties.
     *
     * @param token          The Apify API token.
     * @param resourceType   The resource type (ACTOR or TASK).
     * @param resourceId     The resource ID.
     * @param inboundContext The inbound context path.
     * @return A mocked InboundConnectorContext.
     */
    public static InboundConnectorContext createMockContext(
            String token, ResourceType resourceType, String resourceId, String inboundContext) {
        InboundConnectorContext context = mock(InboundConnectorContext.class);
        ApifyInboundProperties properties = new ApifyInboundProperties(token, resourceType, resourceId);
        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of(
                "inbound", Map.of("context", inboundContext)));
        return context;
    }

    /**
     * Creates a mock WebhookProcessingPayload with the specified body.
     *
     * @param body The webhook body as a string.
     * @return A mocked WebhookProcessingPayload.
     */
    public static WebhookProcessingPayload createMockPayload(String body) {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(Map.of("Content-Type", "application/json"));
        when(payload.params()).thenReturn(Map.of());
        return payload;
    }

    /**
     * Creates a mock WebhookProcessingPayload with custom headers and params.
     *
     * @param body    The webhook body as a string.
     * @param headers The HTTP headers.
     * @param params  The query parameters.
     * @return A mocked WebhookProcessingPayload.
     */
    public static WebhookProcessingPayload createMockPayload(
            String body, Map<String, String> headers, Map<String, String> params) {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(headers);
        when(payload.params()).thenReturn(params);
        return payload;
    }

    /**
     * Default mock initializer for ApifyClient that sets up successful webhook
     * operations.
     * Configures createWebhook to succeed (no longer needs listWebhooksByActor since we use idempotencyKey).
     *
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> defaultActorClientMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
        };
    }

    /**
     * Default mock initializer for ApifyClient that sets up successful webhook
     * operations for tasks.
     * Configures createWebhook to succeed (no longer needs listWebhooksByActorTask since we use idempotencyKey).
     *
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> defaultTaskClientMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that simulates returning an existing webhook via idempotencyKey.
     * When Apify receives a webhook creation request with an idempotencyKey that already exists,
     * it returns the existing webhook instead of creating a new one.
     *
     * @param webhookId  The existing webhook ID.
     * @param requestUrl The webhook callback URL (not used anymore, kept for backwards compatibility).
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> existingActorWebhookMock(
            String webhookId, String requestUrl) {
        return (mock, ctx) -> {
            // Simulate Apify returning existing webhook when idempotencyKey matches
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", webhookId));
            when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that simulates returning an existing webhook via idempotencyKey.
     * When Apify receives a webhook creation request with an idempotencyKey that already exists,
     * it returns the existing webhook instead of creating a new one.
     *
     * @param webhookId  The existing webhook ID.
     * @param requestUrl The webhook callback URL (not used anymore, kept for backwards compatibility).
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> existingTaskWebhookMock(
            String webhookId, String requestUrl) {
        return (mock, ctx) -> {
            // Simulate Apify returning existing webhook when idempotencyKey matches
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", webhookId));
            when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that succeeds at webhook creation.
     * This mock is now equivalent to defaultActorClientMock since we no longer list webhooks.
     * Kept for backwards compatibility with existing tests.
     *
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> listFailsButCreateSucceedsMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that fails webhook creation.
     *
     * @param errorMessage The error message to throw.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> createWebhookFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.createWebhook(anyString(), anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    /**
     * Mock initializer for ApifyClient with full lifecycle support (create and
     * delete).
     *
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> fullLifecycleActorMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
            when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString(), anyString())).thenReturn(responseResult);
        };
    }

    /**
     * Mock initializer for ApifyClient where delete fails.
     *
     * @param errorMessage The error message for delete failure.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> deleteWebhookFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
            when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString(), anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }
}
