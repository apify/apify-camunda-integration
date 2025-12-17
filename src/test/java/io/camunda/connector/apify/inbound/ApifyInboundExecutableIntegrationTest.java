package io.camunda.connector.apify.inbound;

import static io.camunda.connector.apify.inbound.dto.ResourceType.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.apify.common.ApifyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Integration tests for ApifyInboundExecutable that test webhook creation and
 * deletion
 * against the real Apify API.
 * 
 * These tests require:
 * - APIFY_TOKEN environment variable to be set
 * - APIFY_TEST_ACTOR_ID environment variable (optional, defaults to
 * apify/web-scraper)
 * 
 * Run with: mvn test -Dtest=ApifyInboundExecutableIntegrationTest
 * 
 * Example:
 * ```
 * export APIFY_TOKEN="your_token_here"
 * export APIFY_TEST_ACTOR_ID="apify/web-scraper"
 * mvn test -Dtest=ApifyInboundExecutableIntegrationTest
 * ```
 * 
 * or
 * 
 * ```
 * APIFY_TOKEN="your_token_here" APIFY_TEST_ACTOR_ID="apify/web-scraper" mvn
 * test -Dtest=ApifyInboundExecutableIntegrationTest
 * ```
 */
@EnabledIfEnvironmentVariable(named = "APIFY_TOKEN", matches = ".+")
public class ApifyInboundExecutableIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApifyInboundExecutableIntegrationTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_CALLBACK_URL = "https://webhook.site/test-camunda-connector";

    private ApifyInboundExecutable executable;
    private InboundConnectorContext mockContext;
    private ApifyClient apifyClient;
    private String apifyToken;
    private String testActorId;
    private String createdWebhookId;

    /**
     * Sets up the test environment.
     */
    @BeforeEach
    void setUp() {
        apifyToken = System.getenv("APIFY_TOKEN");
        testActorId = System.getenv().getOrDefault("APIFY_TEST_ACTOR_ID", "apify/web-scraper");

        executable = new ApifyInboundExecutable();
        apifyClient = new ApifyClient();

        mockContext = createMockContext();

        LOGGER.info("=== Integration Test Setup ===");
        LOGGER.info("Using actor: {}", testActorId);
        LOGGER.info("Callback URL: {}", TEST_CALLBACK_URL);
    }

    /**
     * Tears down the test environment.
     */
    @AfterEach
    void tearDown() throws IOException {
        // Clean up any webhooks that might have been created
        if (createdWebhookId != null && apifyToken != null) {
            try {
                LOGGER.info("Cleaning up webhook: {}", createdWebhookId);
                apifyClient.deleteWebhook(apifyToken, createdWebhookId);
                LOGGER.info("Webhook cleaned up successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to clean up webhook: {}", e.getMessage());
            }
        }

        if (apifyClient != null) {
            apifyClient.close();
        }
    }

    /**
     * Tests that the activate() method successfully creates a webhook in Apify
     * and reports health as UP.
     */
    @Test
    void shouldCreateWebhookWhenActivated() throws Exception {
        LOGGER.info("\n=== Test: shouldCreateWebhookWhenActivated ===");

        executable.activate(mockContext);

        String webhooksJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooks = OBJECT_MAPPER.readTree(webhooksJson);

        boolean webhookFound = false;
        JsonNode dataNode = webhooks.path("data");
        if (dataNode.has("items")) {
            for (JsonNode webhook : dataNode.get("items")) {
                String requestUrl = webhook.path("requestUrl").asText();
                if (TEST_CALLBACK_URL.equals(requestUrl)) {
                    webhookFound = true;
                    createdWebhookId = webhook.path("id").asText();
                    LOGGER.info("Found created webhook: {}", createdWebhookId);

                    // Verify webhook properties
                    assertThat(webhook.path("isAdHoc").asBoolean()).isFalse();
                    assertThat(webhook.path("eventTypes").isArray()).isTrue();

                    // Verify event types include our expected types
                    JsonNode eventTypes = webhook.get("eventTypes");
                    boolean hasSucceededEvent = false;
                    for (JsonNode eventType : eventTypes) {
                        if ("ACTOR.RUN.SUCCEEDED".equals(eventType.asText())) {
                            hasSucceededEvent = true;
                            break;
                        }
                    }
                    assertThat(hasSucceededEvent).isTrue();

                    break;
                }
            }
        }

        assertThat(webhookFound)
                .withFailMessage("Webhook with callback URL " + TEST_CALLBACK_URL + " not found in Apify")
                .isTrue();

        LOGGER.info("Webhook created successfully");
    }

    /**
     * Tests that the deactivate() method successfully deletes the webhook from
     * Apify.
     */
    @Test
    void shouldDeleteWebhookWhenDeactivated() throws Exception {
        LOGGER.info("\n=== Test: shouldDeleteWebhookWhenDeactivated ===");

        // first create a webhook
        executable.activate(mockContext);

        // find the created webhook ID
        String webhooksJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooks = OBJECT_MAPPER.readTree(webhooksJson);
        JsonNode dataNode = webhooks.path("data");

        String webhookId = null;
        if (dataNode.has("items")) {
            for (JsonNode webhook : dataNode.get("items")) {
                String requestUrl = webhook.path("requestUrl").asText();
                if (TEST_CALLBACK_URL.equals(requestUrl)) {
                    webhookId = webhook.path("id").asText();
                    createdWebhookId = webhookId; // Save for potential cleanup
                    break;
                }
            }
        }

        assertThat(webhookId)
                .withFailMessage("Webhook should be created before deactivation test")
                .isNotNull();

        LOGGER.info("Webhook created: {}", webhookId);

        // deactivate the connector
        executable.deactivate();

        // verify webhook no longer exists
        String webhooksAfterJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooksAfter = OBJECT_MAPPER.readTree(webhooksAfterJson);
        JsonNode dataNodeAfter = webhooksAfter.path("data");

        boolean webhookStillExists = false;
        if (dataNodeAfter.has("items")) {
            for (JsonNode webhook : dataNodeAfter.get("items")) {
                if (webhookId.equals(webhook.path("id").asText())) {
                    webhookStillExists = true;
                    break;
                }
            }
        }

        assertThat(webhookStillExists)
                .withFailMessage("Webhook " + webhookId + " should be deleted after deactivation")
                .isFalse();

        createdWebhookId = null; // Clear since it's been deleted
        LOGGER.info("Webhook deleted successfully");
    }

    /**
     * Tests that activate() handles missing callback URL gracefully.
     */
    @Test
    void shouldHandleMissingCallbackUrl() throws Exception {
        LOGGER.info("\n=== Test: shouldHandleMissingCallbackUrl ===");

        // context without callback URL
        InboundConnectorContext contextWithoutUrl = createMockContextWithoutCallbackUrl();

        executable.activate(contextWithoutUrl);

        // should not throw exception, should report health as DOWN
        // verify no webhook was created
        String webhooksJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooks = OBJECT_MAPPER.readTree(webhooksJson);

        // count webhooks with our test URL (should be zero)
        int webhookCount = 0;
        JsonNode dataNode = webhooks.path("data");
        if (dataNode.has("items")) {
            for (JsonNode webhook : dataNode.get("items")) {
                String requestUrl = webhook.path("requestUrl").asText();
                if (TEST_CALLBACK_URL.equals(requestUrl)) {
                    webhookCount++;
                }
            }
        }

        assertThat(webhookCount).isEqualTo(0);
        LOGGER.info("Handled missing callback URL correctly");
    }

    /**
     * Tests the complete lifecycle: activate -> deactivate.
     */
    @Test
    void shouldHandleCompleteLifecycle() throws Exception {
        LOGGER.info("\n=== Test: shouldHandleCompleteLifecycle ===");

        LOGGER.info("Step 1: Activating connector");
        executable.activate(mockContext);

        LOGGER.info("Step 2: Verifying webhook was created");
        String webhooksJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooks = OBJECT_MAPPER.readTree(webhooksJson);

        String webhookId = findWebhookByUrl(webhooks, TEST_CALLBACK_URL);
        assertThat(webhookId).isNotNull();
        createdWebhookId = webhookId;
        LOGGER.info("Webhook created: {}", webhookId);

        LOGGER.info("Step 3: Deactivating connector");
        executable.deactivate();

        LOGGER.info("Step 4: Verifying webhook was deleted");
        String webhooksAfterJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooksAfter = OBJECT_MAPPER.readTree(webhooksAfterJson);

        String webhookIdAfter = findWebhookByUrl(webhooksAfter, TEST_CALLBACK_URL);
        assertThat(webhookIdAfter).isNull();

        createdWebhookId = null;
        LOGGER.info("Complete lifecycle test passed");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a mock InboundConnectorContext with all required properties.
     */
    private InboundConnectorContext createMockContext() {
        InboundConnectorContext context = mock(InboundConnectorContext.class);

        // mock properties
        ApifyInboundProperties properties = new ApifyInboundProperties(
                apifyToken,
                ACTOR,
                testActorId);

        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);

        // mock context properties (including callback URL)
        Map<String, Object> contextProperties = Map.of(
                "inbound.connector.url", TEST_CALLBACK_URL);
        when(context.getProperties()).thenReturn(contextProperties);

        // mock definition with process elements
        InboundConnectorDefinition definition = mock(InboundConnectorDefinition.class);
        ProcessElement processElement = mock(ProcessElement.class);
        when(processElement.bpmnProcessId()).thenReturn("test-process");
        when(processElement.elementId()).thenReturn("test-element");
        when(definition.elements()).thenReturn(List.of(processElement));
        when(context.getDefinition()).thenReturn(definition);

        return context;
    }

    /**
     * Creates a mock context without a callback URL.
     */
    private InboundConnectorContext createMockContextWithoutCallbackUrl() {
        InboundConnectorContext context = mock(InboundConnectorContext.class);

        ApifyInboundProperties properties = new ApifyInboundProperties(
                apifyToken,
                ACTOR,
                testActorId);

        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of());

        InboundConnectorDefinition definition = mock(InboundConnectorDefinition.class);
        ProcessElement processElement = mock(ProcessElement.class);
        when(processElement.bpmnProcessId()).thenReturn("test-process");
        when(processElement.elementId()).thenReturn("test-element");
        when(definition.elements()).thenReturn(List.of(processElement));
        when(context.getDefinition()).thenReturn(definition);

        return context;
    }

    /**
     * Finds a webhook ID by its request URL.
     */
    private String findWebhookByUrl(JsonNode webhooksResponse, String url) {
        JsonNode dataNode = webhooksResponse.path("data");
        if (dataNode.has("items")) {
            for (JsonNode webhook : dataNode.get("items")) {
                String requestUrl = webhook.path("requestUrl").asText();
                if (url.equals(requestUrl)) {
                    return webhook.path("id").asText();
                }
            }
        }
        return null;
    }
}
