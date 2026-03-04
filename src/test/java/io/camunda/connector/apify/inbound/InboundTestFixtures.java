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
     * Private constructor to prevent instantiation.
     */
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
     * Default mock initializer for ApifyClient that sets up successful webhook operations.
     *
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> defaultActorClientMock() {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.responseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that returns an existing webhook with a custom ID.
     *
     * @param webhookId The webhook ID to return.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> webhookCreationMockWithId(String webhookId) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.responseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", webhookId));
            when(mock.createWebhook(anyString())).thenReturn(createResult);
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
            when(mock.createWebhook(anyString()))
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
            when(responseResult.responseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString())).thenReturn(responseResult);
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
            when(responseResult.responseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(responseResult);
            when(mock.deleteWebhook(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    /**
     * Mock initializer for ApifyClient that resolves an Actor slug to a real ID
     * and sets up successful webhook creation.
     *
     * @param resolvedActorId The resolved Actor ID to return from getActor.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> actorSlugResolutionMock(String resolvedActorId) {
        return (mock, ctx) -> {
            // Mock getActor to return the resolved ID
            ApifyClient.ResponseResult actorResult = mock(ApifyClient.ResponseResult.class);
            when(actorResult.responseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", resolvedActorId));
            when(mock.getActor(anyString())).thenReturn(actorResult);

            // Mock webhook creation
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.responseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient that resolves a Task slug to a real ID
     * and sets up successful webhook creation.
     *
     * @param resolvedTaskId The resolved Task ID to return from getTask.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> taskSlugResolutionMock(String resolvedTaskId) {
        return (mock, ctx) -> {
            // Mock getTask to return the resolved ID
            ApifyClient.ResponseResult taskResult = mock(ApifyClient.ResponseResult.class);
            when(taskResult.responseBody()).thenReturn(
                    String.format("{\"data\":{\"id\":\"%s\"}}", resolvedTaskId));
            when(mock.getTask(anyString())).thenReturn(taskResult);

            // Mock webhook creation
            ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
            when(createResult.responseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
            when(mock.createWebhook(anyString())).thenReturn(createResult);
        };
    }

    /**
     * Mock initializer for ApifyClient where Actor resolution fails.
     *
     * @param errorMessage The error message to throw.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> actorResolutionFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.getActor(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    /**
     * Mock initializer for ApifyClient where Task resolution fails.
     *
     * @param errorMessage The error message to throw.
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> taskResolutionFailsMock(String errorMessage) {
        return (mock, ctx) -> {
            when(mock.getTask(anyString()))
                    .thenThrow(new IOException(errorMessage));
        };
    }

    /**
     * Mock initializer for ApifyClient where the API response is missing the data.id field.
     *
     * @param responseBody The JSON response body to return (missing data.id).
     * @return A MockInitializer for ApifyClient.
     */
    public static MockedConstruction.MockInitializer<ApifyClient> actorResolutionMissingIdMock(String responseBody) {
        return (mock, ctx) -> {
            ApifyClient.ResponseResult actorResult = mock(ApifyClient.ResponseResult.class);
            when(actorResult.responseBody()).thenReturn(responseBody);
            when(mock.getActor(anyString())).thenReturn(actorResult);
        };
    }
}
