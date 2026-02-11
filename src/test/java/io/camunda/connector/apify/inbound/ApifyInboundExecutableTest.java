package io.camunda.connector.apify.inbound;

import static io.camunda.connector.apify.inbound.InboundTestFixtures.OBJECT_MAPPER;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.VALID_WEBHOOK_RESPONSE;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.createMockContext;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.createMockPayload;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.createWebhookFailsMock;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.defaultActorClientMock;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.deleteWebhookFailsMock;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.webhookCreationMockWithId;
import static io.camunda.connector.apify.inbound.InboundTestFixtures.fullLifecycleActorMock;
import static io.camunda.connector.apify.inbound.dto.ResourceType.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.common.dto.Authentication;
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
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for ApifyInboundExecutable.
 */
@ExtendWith(MockitoExtension.class)
class ApifyInboundExecutableTest {

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
                    new Authentication("test-token"),
                    ACTOR,
                    "my-actor-id");

            assertThat(properties.authentication().token()).isEqualTo("test-token");
            assertThat(properties.resourceType()).isEqualTo(ACTOR);
            assertThat(properties.resourceId()).isEqualTo("my-actor-id");
        }

        @Nested
        @DisplayName("Resource ID Normalization")
        class ResourceIdNormalization {

            @Test
            void shouldNormalizeResourceIdWithSlash() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        new Authentication("test-token"),
                        ACTOR,
                        "apify/google-search-scraper");

                String normalized = properties.getNormalizedResourceId();

                assertThat(normalized).isEqualTo("apify~google-search-scraper");
            }

            @Test
            void shouldNotChangeResourceIdWithoutSlash() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        new Authentication("test-token"),
                        ACTOR,
                        "my-actor-id-123");

                String normalized = properties.getNormalizedResourceId();

                assertThat(normalized).isEqualTo("my-actor-id-123");
            }

            @Test
            void shouldHandleMultipleSlashesInResourceId() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        new Authentication("test-token"),
                        ACTOR,
                        "username/category/actor-name");

                String normalized = properties.getNormalizedResourceId();

                assertThat(normalized).isEqualTo("username~category~actor-name");
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
                        new Authentication(""),
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("authentication.token");
            }

            @Test
            void shouldRejectNullToken() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        new Authentication(null),
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("authentication.token");
            }

            @Test
            void shouldRejectNullAuthentication() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        null,
                        ACTOR,
                        "my-actor-id");

                Set<ConstraintViolation<ApifyInboundProperties>> violations = validator.validate(properties);

                assertThat(violations).isNotEmpty();
                assertThat(violations)
                        .extracting(v -> v.getPropertyPath().toString())
                        .contains("authentication");
            }

            @Test
            void shouldRejectNullResourceType() {
                ApifyInboundProperties properties = new ApifyInboundProperties(
                        new Authentication("test-token"),
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
                        new Authentication("test-token"),
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
                        new Authentication("test-token"),
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
                        new Authentication("test-token"),
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

    }

    @Nested
    @DisplayName("Webhook Processing")
    class WebhookProcessingTests {

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

            WebhookProcessingPayload payload = createMockPayload(webhookBody);

            WebhookResult result = executable.triggerWebhook(payload);

            assertThat(result).isNotNull();
            assertThat(result.connectorData()).containsKey("eventType");
            assertThat(result.connectorData().get("eventType")).isEqualTo("ACTOR.RUN.SUCCEEDED");
            assertThat(result.connectorData().get("runId")).isEqualTo("run123");
            assertThat(result.connectorData().get("status")).isEqualTo("SUCCEEDED");
        }

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

        @Test
        void shouldReturnErrorResultForMalformedJson() throws Exception {
            String malformedJson = "{ this is not valid json }";

            WebhookProcessingPayload payload = createMockPayload(malformedJson);

            WebhookResult result = executable.triggerWebhook(payload);

            assertThat(result).isNotNull();
            assertThat(result.connectorData()).containsKey("error");
            assertThat(result.connectorData().get("error").toString()).contains("Failed to parse Apify webhook body");
        }

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

            WebhookProcessingPayload payload = createMockPayload(webhookBody, headers, params);

            WebhookResult result = executable.triggerWebhook(payload);

            assertThat(result).isNotNull();
            assertThat(result.request()).isNotNull();
            assertThat(result.request().headers()).isEqualTo(headers);
            assertThat(result.request().params()).isEqualTo(params);
        }

    }

    @Nested
    @DisplayName("Activation")
    class ActivationTests {

        @Test
        void shouldActivateSuccessfullyAndReportHealthUp() throws Exception {
            InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id", "test-context-123");

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    defaultActorClientMock())) {

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
                    createWebhookFailsMock("Network error"))) {

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

        @Nested
        @DisplayName("Idempotency Key Behavior")
        class IdempotencyKeyBehavior {

            @Test
            void shouldReuseExistingWebhookViaIdempotencyKey() throws Exception {
                InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id",
                        "test-context-123");

                try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                        webhookCreationMockWithId("existing-webhook-456"))) {

                    executable.activate(context);

                    ApifyClient constructedClient = mockedClient.constructed().get(0);
                    verify(constructedClient).createWebhook(eq("test-token"), anyString());

                    ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                    verify(context).reportHealth(healthCaptor.capture());
                    assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.UP);
                }
            }

            @Test
            void shouldCreateWebhookWithIdempotencyKey() throws Exception {
                InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id",
                        "test-context-123");

                try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                        defaultActorClientMock())) {

                    executable.activate(context);

                    ApifyClient constructedClient = mockedClient.constructed().get(0);
                    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
                    verify(constructedClient).createWebhook(eq("test-token"), payloadCaptor.capture());

                    // Verify idempotencyKey is included in the payload
                    String payload = payloadCaptor.getValue();
                    assertThat(payload).contains("\"idempotencyKey\"");
                    assertThat(payload).contains("http://example.com/inbound/test-context-123");

                    ArgumentCaptor<Health> healthCaptor = ArgumentCaptor.forClass(Health.class);
                    verify(context).reportHealth(healthCaptor.capture());
                    assertThat(healthCaptor.getValue().getStatus()).isEqualTo(Health.Status.UP);
                }
            }

            @Test
            void shouldIncludeHashedIdempotencyKeyInWebhookPayload() throws Exception {
                InboundConnectorContext context = createMockContext("test-token", ACTOR, "my-actor-id",
                        "test-context-123");

                try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                        defaultActorClientMock())) {

                    executable.activate(context);

                    ApifyClient constructedClient = mockedClient.constructed().get(0);
                    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
                    verify(constructedClient).createWebhook(eq("test-token"), payloadCaptor.capture());

                    // Pre-computed SHA-256 of "http://example.com/inbound/test-context-123:my-actor-id"
                    String payload = payloadCaptor.getValue();
                    assertThat(payload).contains("\"idempotencyKey\":\"df03eda4ff44752c70dc627cd34800a7f7293785b5d61604bd14f6b788ba7cb2\"");
                }
            }

            @Test
            void shouldGenerateDeterministicIdempotencyKey() {
                // Pre-computed SHA-256 of "http://example.com/callback:actor-123"
                String key = ApifyInboundExecutable.generateIdempotencyKey(
                        "http://example.com/callback", "actor-123");

                assertThat(key)
                        .as("Idempotency key should be a deterministic SHA-256 hex string")
                        .hasSize(64)
                        .matches("[0-9a-f]{64}");

                // Same inputs must always produce the same key
                String key2 = ApifyInboundExecutable.generateIdempotencyKey(
                        "http://example.com/callback", "actor-123");
                assertThat(key).isEqualTo(key2);

                // Different inputs must produce different keys
                String differentKey = ApifyInboundExecutable.generateIdempotencyKey(
                        "http://example.com/callback", "actor-456");
                assertThat(key).isNotEqualTo(differentKey);
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
                    fullLifecycleActorMock())) {

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
                    deleteWebhookFailsMock("Failed to delete webhook"))) {

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
                    createWebhookFailsMock("Network error"))) {

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
                    defaultActorClientMock())) {

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
                // Pre-computed SHA-256 of "http://example.com/inbound/test-context-123:apify~google-search"
                assertThat(payload).contains("\"idempotencyKey\":\"cca6a64107e0e5688e4d049dc98691dcd0d6f71ed06744e5e22104a840dd18a1\"");
            }
        }

    }

    @Nested
    @DisplayName("Callback URL Configuration")
    class CallbackUrlConfigurationTests {

        @Test
        void shouldThrowWhenInboundContextIsMissing() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties(
                    new Authentication("test-token"), ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of()); // No "inbound" key

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inbound context configuration is missing");
        }

        @Test
        void shouldThrowWhenContextPathIsMissing() {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties(
                    new Authentication("test-token"), ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of("inbound", Map.of())); // Empty inbound, no "context" key

            assertThatThrownBy(() -> executable.activate(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Inbound context path is not configured");
        }

        @Test
        void shouldHandleContextWithLeadingSlash() throws Exception {
            InboundConnectorContext context = mock(InboundConnectorContext.class);
            ApifyInboundProperties properties = new ApifyInboundProperties(
                    new Authentication("test-token"), ACTOR, "my-actor-id");
            when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
            when(context.getProperties()).thenReturn(Map.of("inbound", Map.of("context", "/leading-slash-context")));

            try (MockedConstruction<ApifyClient> mockedClient = mockConstruction(ApifyClient.class,
                    defaultActorClientMock())) {

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
    }
}
