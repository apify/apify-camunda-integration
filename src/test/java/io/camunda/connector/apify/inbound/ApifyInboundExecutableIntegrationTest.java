package io.camunda.connector.apify.inbound;

import static io.camunda.connector.apify.inbound.InboundTestFixtures.OBJECT_MAPPER;
import static io.camunda.connector.apify.inbound.dto.ResourceType.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.apify.common.ApifyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Integration tests for ApifyInboundExecutable that test webhook creation and
 * deletion against the real Apify API.
 *
 * These tests require:
 * - APIFY_TOKEN environment variable to be set
 * - APIFY_TEST_ACTOR_ID environment variable (optional, defaults to
 * apify/web-scraper)
 * - CONNECTOR_BASE_URL environment variable to be set
 *
 * Run with: mvn test -Dtest=ApifyInboundExecutableIntegrationTest
 *
 * Example:
 * {@code
 * export APIFY_TOKEN="your_token_here"
 * export APIFY_TEST_ACTOR_ID="apify/web-scraper"
 * export CONNECTOR_BASE_URL="https://webhook.site"
 * mvn test -Dtest=ApifyInboundExecutableIntegrationTest
 * }
 */
@EnabledIfEnvironmentVariable(named = "APIFY_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CONNECTOR_BASE_URL", matches = ".+")
class ApifyInboundExecutableIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApifyInboundExecutableIntegrationTest.class);
    private static final String TEST_INBOUND_CONTEXT = "test-camunda-connector";

    private ApifyInboundExecutable executable;
    private InboundConnectorContext mockContext;
    private ApifyClient apifyClient;
    private String apifyToken;
    private String testActorId;
    private String createdWebhookId;
    private String expectedCallbackUrl;

    @BeforeEach
    void setUp() {
        apifyToken = System.getenv("APIFY_TOKEN");
        testActorId = System.getenv().getOrDefault("APIFY_TEST_ACTOR_ID", "apify/web-scraper");
        String baseUrl = System.getenv("CONNECTOR_BASE_URL");

        expectedCallbackUrl = buildExpectedCallbackUrl(baseUrl, TEST_INBOUND_CONTEXT);

        executable = new ApifyInboundExecutable();
        apifyClient = new ApifyClient();
        mockContext = createMockContext();

        LOGGER.info("=== Integration Test Setup ===");
        LOGGER.info("Using actor: {}", testActorId);
        LOGGER.info("Expected callback URL: {}", expectedCallbackUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
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

    @Test
    @DisplayName("Should handle complete webhook lifecycle: create and delete")
    void shouldHandleCompleteLifecycle() throws Exception {
        LOGGER.info("\n=== Test: shouldHandleCompleteLifecycle ===");

        LOGGER.info("Step 1: Activating connector");
        executable.activate(mockContext);

        LOGGER.info("Step 2: Verifying webhook was created");
        String webhooksJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooks = OBJECT_MAPPER.readTree(webhooksJson);

        String webhookId = findWebhookByUrl(webhooks, expectedCallbackUrl);
        assertThat(webhookId)
                .withFailMessage("Webhook with callback URL " + expectedCallbackUrl + " not found in Apify")
                .isNotNull();
        createdWebhookId = webhookId;
        LOGGER.info("Webhook created: {}", webhookId);

        LOGGER.info("Step 3: Deactivating connector");
        executable.deactivate();

        LOGGER.info("Step 4: Verifying webhook was deleted");
        String webhooksAfterJson = apifyClient.listWebhooks(apifyToken).getResponseBody();
        JsonNode webhooksAfter = OBJECT_MAPPER.readTree(webhooksAfterJson);

        String webhookIdAfter = findWebhookByUrl(webhooksAfter, expectedCallbackUrl);
        assertThat(webhookIdAfter)
                .withFailMessage("Webhook " + webhookId + " should be deleted after deactivation")
                .isNull();

        createdWebhookId = null;
        LOGGER.info("Complete lifecycle test passed");
    }

    private InboundConnectorContext createMockContext() {
        InboundConnectorContext context = mock(InboundConnectorContext.class);

        ApifyInboundProperties properties = new ApifyInboundProperties(
                apifyToken,
                ACTOR,
                testActorId);

        when(context.bindProperties(ApifyInboundProperties.class)).thenReturn(properties);
        when(context.getProperties()).thenReturn(Map.of(
                "inbound", Map.of("context", TEST_INBOUND_CONTEXT)));

        return context;
    }

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

    private String buildExpectedCallbackUrl(String baseUrl, String contextValue) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (contextValue.startsWith("/")) {
            contextValue = contextValue.substring(1);
        }
        return baseUrl + "/inbound/" + contextValue;
    }
}
