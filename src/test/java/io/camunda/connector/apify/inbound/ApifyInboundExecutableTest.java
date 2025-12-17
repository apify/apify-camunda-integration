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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for ApifyInboundExecutable.
 */
@ExtendWith(MockitoExtension.class)
class ApifyInboundExecutableTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String VALID_WEBHOOK_RESPONSE = """
            {"data":{"id":"webhook-123"}}
            """;
    private static final String EMPTY_WEBHOOKS_LIST = """
            {"data":{"items":[]}}
            """;

    private ApifyInboundExecutable executable;

    @BeforeEach
    void setUp() {
        executable = new ApifyInboundExecutable();
    }

    @Nested
    @DisplayName("ApifyInboundProperties")
    class ApifyInboundPropertiesTests {

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

        @Nested
        @DisplayName("Resource ID Normalization")
        class ResourceIdNormalization {

    @Test
    void shouldNormalizeResourceIdWithSlash() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "apify/google-search-scraper");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("apify~google-search-scraper");
    }

    @Test
    void shouldNotChangeResourceIdWithoutSlash() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                ACTOR,
                "my-actor-id-123");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("my-actor-id-123");
    }

    @Test
    void shouldHandleMultipleSlashesInResourceId() {
        ApifyInboundProperties properties = new ApifyInboundProperties(
                "test-token",
                TASK,
                "username/category/task-name");

        String normalized = properties.getNormalizedResourceId();

        assertThat(normalized).isEqualTo("username~category~task-name");
            }
        }

        @Nested
        @DisplayName("Bean Validation")
        class BeanValidation {

            private Validator validator;

            @BeforeEach
            void setUp() {
                try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                    validator = factory.getValidator();
                }
            }

            @Test
            void shouldRejectEmptyToken() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        "",
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("token");
            }

            @Test
            void shouldRejectNullToken() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        null,
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("token");
            }

            @Test
            void shouldRejectNullResourceType() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        "test-token",
                        null,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("resourceType");
            }

            @Test
            void shouldRejectEmptyResourceId() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        "test-token",
                        ACTOR,
                        "");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("resourceId");
            }

            @Test
            void shouldRejectNullResourceId() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        "test-token",
                        ACTOR,
                        null);

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("resourceId");
            }

            @Test
            void shouldAcceptValidProperties() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        "test-token",
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("ApifyInboundEvent Parsing")
    class ApifyInboundEventTests {

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

        @Test
        void shouldHandleEventWithResourceButMissingFields() throws Exception {
            String eventJson = """
                    {
                        "eventType": "ACTOR.RUN.SUCCEEDED",
                        "resource": {
                            "id": "run123"
                        }
                    }
                    """;

            ApifyInboundEvent event = OBJECT_MAPPER.readValue(eventJson, ApifyInboundEvent.class);

            assertThat(event.getRunId()).isEqualTo("run123");
            assertThat(event.getActorId()).isNull();
            assertThat(event.getTaskId()).isNull();
            assertThat(event.getStatus()).isNull();
            assertThat(event.getDefaultDatasetId()).isNull();
            assertThat(event.getDefaultKeyValueStoreId()).isNull();
        }
    }

    @Nested
    @DisplayName("Webhook Processing")
    class WebhookProcessingTests {

    @Test
        void shouldProcessValidWebhookPayload() {
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

            WebhookProcessingPayload payload = createMockPayload(webhookBody);

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("eventType");
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.SUCCEEDED");
        assertThat(result.connectorData().get("runId")).isEqualTo("run123");
        assertThat(result.connectorData().get("status")).isEqualTo("SUCCEEDED");
    }

    @Test
        void shouldReturnErrorResultForEmptyBody() {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
        when(payload.rawBody()).thenReturn(null);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
    }

    @Test
        void shouldReturnErrorResultForEmptyByteArray() {
        WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
            when(payload.rawBody()).thenReturn(new byte[0]);
        when(payload.headers()).thenReturn(Map.of());
        when(payload.params()).thenReturn(Map.of());

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
            assertThat(result.connectorData()).containsKey("error");
    }

    @Test
        void shouldReturnErrorResultForMalformedJson() {
        String malformedJson = "{ this is not valid json }";

            WebhookProcessingPayload payload = createMockPayload(malformedJson);

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData()).containsKey("error");
        assertThat(result.connectorData().get("error").toString()).contains("Failed to parse webhook body");
    }

        @Nested
        @DisplayName("Different Event Statuses")
        class DifferentEventStatuses {

    @Test
            void shouldProcessWebhookWithFailedStatus() {
                String webhookBody = """
                {
                            "eventType": "ACTOR.RUN.FAILED",
                    "resource": {
                                "id": "run456",
                                "status": "FAILED"
                    }
                }
                """;

                WebhookProcessingPayload payload = createMockPayload(webhookBody);

                WebhookResult result = triggerWebhookSafely(payload);

                assertThat(result).isNotNull();
                assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.FAILED");
                assertThat(result.connectorData().get("status")).isEqualTo("FAILED");
            }

    @Test
            void shouldProcessWebhookWithTimedOutStatus() {
        String webhookBody = """
                {
                    "eventType": "ACTOR.RUN.TIMED_OUT",
                    "resource": {
                        "id": "run789",
                        "status": "TIMED-OUT"
                    }
                }
                """;

                WebhookProcessingPayload payload = createMockPayload(webhookBody);

                WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.TIMED_OUT");
        assertThat(result.connectorData().get("status")).isEqualTo("TIMED-OUT");
    }

    @Test
            void shouldProcessWebhookWithAbortedStatus() {
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

                WebhookProcessingPayload payload = createMockPayload(webhookBody);

                WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.ABORTED");
        assertThat(result.connectorData().get("status")).isEqualTo("ABORTED");
        assertThat(result.connectorData().get("actorId")).isEqualTo("actor999");
            }
    }

    @Test
        void shouldIncludeHeadersAndParamsInSuccessResult() {
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

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.request()).isNotNull();
        assertThat(result.request().headers()).isEqualTo(headers);
        assertThat(result.request().params()).isEqualTo(params);
    }

    @Test
        void shouldProcessWebhookWithTaskResource() {
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

            WebhookProcessingPayload payload = createMockPayload(webhookBody);

            WebhookResult result = triggerWebhookSafely(payload);

        assertThat(result).isNotNull();
        assertThat(result.connectorData().get("taskId")).isEqualTo("task456");
        assertThat(result.connectorData().get("runId")).isEqualTo("run456");
    }

        private WebhookProcessingPayload createMockPayload(String body) {
            WebhookProcessingPayload payload = mock(WebhookProcessingPayload.class);
            when(payload.rawBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
            when(payload.headers()).thenReturn(Map.of("Content-Type", "application/json"));
            when(payload.params()).thenReturn(Map.of());
            return payload;
        }

        private WebhookResult triggerWebhookSafely(WebhookProcessingPayload payload) {
            try {
                return executable.triggerWebhook(payload);
            } catch (Exception e) {
                throw new RuntimeException("Unexpected exception in triggerWebhook", e);
            }
        }
    }

    @Nested
    @DisplayName("Activation")
    class ActivationTests {

    @Test
        void shouldActivateSuccessfullyAndReportHealthUp() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                        when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                    })) {

                executable.activate(context);

                ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                verify(context).reportHealth(healthCaptor.capture());
                assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.UP);

                assertThat(mockedClient.constructed()).hasSize(1);
                ApifyClient constructedClient = mockedClient.constructed().get(0);
                verify(constructedClient).createWebhook(eq("test-token"), anyString());
            }
        }

        @Test
        void shouldReportHealthDownAndCleanupWhenActivationFails() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        when(mock.createWebhook(anyString(), anyString()))
                                .thenThrow(new IOException("Network error"));
                    })) {

                assertThatThrownBy(() -> executable.activate(context))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("Network error");

                ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                verify(context).reportHealth(healthCaptor.capture());
                assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.DOWN);

                assertThat(mockedClient.constructed()).hasSize(1);
                ApifyClient constructedClient = mockedClient.constructed().get(0);
                verify(constructedClient).close();
            }
        }

        @Test
        void shouldHandleMissingInboundContext() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of("inbound", Map.of()));

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        when(mock.createWebhook(anyString(), anyString()))
                                .thenThrow(new IOException("Invalid callback URL"));
                    })) {

                assertThatThrownBy(() -> executable.activate(context))
                        .isInstanceOf(Exception.class);
            }
        }

        @Nested
        @DisplayName("Existing Webhook Reuse")
        class ExistingWebhookReuse {

    @Test
    void shouldReuseExistingWebhookIfFound() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");
        String listWebhooksResponse = """
                {
                    "data": {
                        "items": [
                            {
                                "id": "existing-webhook-456",
                                "requestUrl": "http://example.com/inbound/test-context-123"
                            }
                        ]
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                    when(listResult.getResponseBody()).thenReturn(listWebhooksResponse);
                    when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);
                })) {

            executable.activate(context);

            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).listWebhooksByActor(eq("test-token"), eq("my-actor-id"));
            verify(constructedClient, never()).createWebhook(anyString(), anyString());

                    ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                    verify(context).reportHealth(healthCaptor.capture());
                    assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.UP);
        }
    }

    @Test
    void shouldReuseExistingWebhookForTaskResourceType() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", TASK, "my-task-id", "test-context-789");
        String listWebhooksResponse = """
                {
                    "data": {
                        "items": [
                            {
                                "id": "existing-task-webhook-789",
                                "requestUrl": "http://example.com/inbound/test-context-789"
                            }
                        ]
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                    when(listResult.getResponseBody()).thenReturn(listWebhooksResponse);
                    when(mock.listWebhooksByActorTask(anyString(), anyString())).thenReturn(listResult);
                })) {

            executable.activate(context);

            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).listWebhooksByActorTask(eq("test-token"), eq("my-task-id"));
            verify(constructedClient, never()).createWebhook(anyString(), anyString());
        }
    }

    @Test
    void shouldProceedWithCreationIfListWebhooksFails() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    when(mock.listWebhooksByActor(anyString(), anyString()))
                            .thenThrow(new IOException("API error while listing webhooks"));

                    ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
                            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                    when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
                })) {

            executable.activate(context);

            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).listWebhooksByActor(eq("test-token"), eq("my-actor-id"));
            verify(constructedClient).createWebhook(eq("test-token"), anyString());

                    ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                    verify(context).reportHealth(healthCaptor.capture());
                    assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.UP);
        }
    }

    @Test
    void shouldCreateNewWebhookIfExistingWebhookHasDifferentCallbackUrl() throws Exception {
        InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");
        String listWebhooksResponse = """
                {
                    "data": {
                        "items": [
                            {
                                "id": "different-webhook-999",
                                "requestUrl": "http://different-url.com/inbound/other-context"
                            }
                        ]
                    }
                }
                """;

        try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                (mock, ctx) -> {
                    ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                    when(listResult.getResponseBody()).thenReturn(listWebhooksResponse);
                    when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                    ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
                            when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                    when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
                })) {

            executable.activate(context);

            ApifyClient constructedClient = mockedClient.constructed().get(0);
            verify(constructedClient).listWebhooksByActor(eq("test-token"), eq("my-actor-id"));
            verify(constructedClient).createWebhook(eq("test-token"), anyString());
                }
            }
        }
    }

    @Nested
    @DisplayName("Deactivation")
    class DeactivationTests {

        @Test
        void shouldDeactivateSuccessfullyAndDeleteWebhook() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                        when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                        when(mock.deleteWebhook(anyString(), anyString())).thenReturn(responseResult);
                    })) {

                executable.activate(context);
                executable.deactivate();

                assertThat(mockedClient.constructed()).hasSize(1);
                ApifyClient constructedClient = mockedClient.constructed().get(0);
                verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
                verify(constructedClient).close();
            }
        }

        @Test
        void shouldHandleWebhookDeletionFailureGracefully() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult responseResult = mock(ApifyClient.ResponseResult.class);
                        when(responseResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(responseResult);
                        when(mock.deleteWebhook(anyString(), anyString()))
                                .thenThrow(new IOException("Failed to delete webhook"));
                    })) {

                executable.activate(context);

                // should not throw even though deletion fails
                executable.deactivate();

                ApifyClient constructedClient = mockedClient.constructed().get(0);
                verify(constructedClient).deleteWebhook(eq("test-token"), eq("webhook-123"));
                verify(constructedClient).close();
            }
        }

        @Test
        void shouldHandleDeactivateWithoutPriorActivation() throws Exception {
            // should not throw
            executable.deactivate();
        }

        @Test
        void shouldCloseClientEvenWhenNoWebhookWasCreated() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        when(mock.createWebhook(anyString(), anyString()))
                                .thenThrow(new IOException("Network error"));
                    })) {

                try {
                    executable.activate(context);
                } catch (IOException e) {
                    // expected
                }

                executable.deactivate();

                ApifyClient constructedClient = mockedClient.constructed().get(0);
                verify(constructedClient).close();
                verify(constructedClient, never()).deleteWebhook(anyString(), anyString());
            }
        }
    }

    @Nested
    @DisplayName("Webhook Payload Building")
    class WebhookPayloadBuildingTests {

        @Test
        void shouldBuildCorrectWebhookPayloadForActorResource() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "apify/google-search",
                    "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
                        when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
                    })) {

                executable.activate(context);

                ApifyClient constructedClient = mockedClient.constructed().get(0);
                ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
                verify(constructedClient).createWebhook(eq("test-token"), payloadCaptor.capture());

                String payload = payloadCaptor.getValue();

                assertThat(payload).contains("\"eventTypes\"");
                assertThat(payload).contains("ACTOR.RUN.SUCCEEDED");
                assertThat(payload).contains("ACTOR.RUN.FAILED");
                assertThat(payload).contains("ACTOR.RUN.TIMED_OUT");
                assertThat(payload).contains("ACTOR.RUN.ABORTED");
                assertThat(payload).contains("\"condition\"");
                assertThat(payload).contains("\"actorId\"");
                assertThat(payload).contains("apify~google-search");
                assertThat(payload).contains("\"requestUrl\"");
                assertThat(payload).contains("http://example.com/inbound/test-context-123");
                assertThat(payload).contains("\"payloadTemplate\"");
                assertThat(payload).contains("\"shouldInterpolateStrings\":true");
            }
        }

        @Test
        void shouldBuildCorrectWebhookPayloadForTaskResource() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", TASK, "my-task-id", "test-context-456");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActorTask(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
                        when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
                    })) {

                executable.activate(context);

                ApifyClient constructedClient = mockedClient.constructed().get(0);
                ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
                verify(constructedClient).createWebhook(eq("test-token"), payloadCaptor.capture());

                String payload = payloadCaptor.getValue();

                assertThat(payload).contains("\"actorTaskId\"");
                assertThat(payload).contains("my-task-id");
            }
        }
    }

    @Nested
    @DisplayName("Callback URL Configuration")
    class CallbackUrlConfigurationTests {

        @Test
        void shouldThrowWhenInboundContextIsMissing() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of()); // No "inbound" key

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inbound context configuration is missing");
        }

        @Test
        void shouldThrowWhenContextPathIsMissing() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of("inbound", Map.of())); // Empty inbound, no "context" key

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inbound context path is not configured");
        }

        @Test
        void shouldHandleContextWithLeadingSlash() throws Exception {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of("inbound", Map.of("context", "/leading-slash-context")));

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    (mock, ctx) -> {
                        ApifyClient.ResponseResult listResult = mock(ApifyClient.ResponseResult.class);
                        when(listResult.getResponseBody()).thenReturn(EMPTY_WEBHOOKS_LIST);
                        when(mock.listWebhooksByActor(anyString(), anyString())).thenReturn(listResult);

                        ApifyClient.ResponseResult createResult = mock(ApifyClient.ResponseResult.class);
                        when(createResult.getResponseBody()).thenReturn(VALID_WEBHOOK_RESPONSE);
                        when(mock.createWebhook(anyString(), anyString())).thenReturn(createResult);
                    })) {

                executable.activate(context);

                ApifyClient constructedClient = mockedClient.constructed().get(0);
                ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
                verify(constructedClient).createWebhook(eq("test-token"), payloadCaptor.capture());

                String payload = payloadCaptor.getValue();
                // Should normalize the URL without double slashes
                assertThat(payload).contains("/inbound/leading-slash-context");
                assertThat(payload).doesNotContain("//leading-slash-context");
            }
        }

        @Test
        void shouldHandleInboundContextAsNullInMap() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties("test-token", ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);

            java.util.HashMap<String, Object> inboundMap = new java.util.HashMap<>();
            inboundMap.put("context", null);
            when(context.getProperties()).thenReturn(Map.of("inbound", inboundMap));

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inbound context path is not configured");
        }
    }

    // Helper method to create mock context
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
