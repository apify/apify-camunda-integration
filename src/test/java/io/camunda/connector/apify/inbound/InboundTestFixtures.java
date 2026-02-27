package io.camunda.connector.apify.inbound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.inbound.dto.ResourceType;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared test fixtures and utilities for inbound connector tests.
 */
public final class InboundTestFixtures {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String VALID_WEBHOOK_RESPONSE = """
            {"data":{"id":"webhook-123"}}
            """;

    private InboundTestFixtures() {
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
        ApifyInboundProperties properties = new ApifyInboundProperties(
                new Authentication(token), resourceType, resourceId);
        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of(
                "inbound", Map.of("context", inboundContext)));
        return context;
    }

    /**
     * Creates a mock WebhookProcessingPayload with the specified body.
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
     */
    public static WebhookProcessingPayload createMockPayload(
            String body, Map<String, String> headers, Map<String, String> params) {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(payload.headers()).thenReturn(headers);
        when(payload.params()).thenReturn(params);
        return payload;
    }

    public static MockedConstruction.MockInitializer<ApifyClient> defaultActorClientMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> webhookCreationMockWithId(String webhookId) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", webhookId));
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> createWebhookFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.createWebhook(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> fullLifecycleActorMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
            when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString())).thenReturn(responseResult);
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> deleteWebhookFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
            when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> actorSlugResolutionMock(String resolvedActorId) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult actorResult = mock(ApifyClient.ResponseResult.class);
            when(actorResult.getStatusCode()).thenReturn(200);
            when(actorResult.getResponseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", resolvedActorId));
            when(mock.getActor(anyString())).thenReturn(actorResult);

            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> taskSlugResolutionMock(String resolvedTaskId) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult taskResult = mock(ApifyClient.ResponseResult.class);
            when(taskResult.getStatusCode()).thenReturn(200);
            when(taskResult.getResponseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", resolvedTaskId));
            when(mock.getTask(anyString())).thenReturn(taskResult);

            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> actorResolutionFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.getActor(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> taskResolutionFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.getTask(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    public static MockedConstruction.MockInitializer<ApifyClient> actorResolutionMissingIdMock(String responseBody) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult actorResult = mock(ApifyClient.ResponseResult.class);
            when(actorResult.getStatusCode()).thenReturn(200);
            when(actorResult.getResponseBody()).thenReturn(responseBody);
            when(mock.getActor(anyString())).thenReturn(actorResult);
        };
    }
}
