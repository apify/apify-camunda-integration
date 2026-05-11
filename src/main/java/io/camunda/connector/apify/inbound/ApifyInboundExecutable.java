package io.camunda.connector.apify.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.common.URLValidator;
import io.camunda.connector.apify.inbound.dto.ApifyWebhookResponse;
import io.camunda.connector.apify.inbound.dto.ApifyPayloadTemplate;
import io.camunda.connector.apify.inbound.dto.ResourceType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Apify Inbound Connector implementation that listens for Apify webhook events.
 */
@InboundConnector(name = "Apify Inbound Connector", type = "io.camunda:apify-inbound:1")
@ElementTemplate(id = "io.camunda.connector.inbound.Apify.v1", name = "Apify Connector", version = 1, description = "Creates an Apify webhook for completed Actor or Task runs, receives event updates, and automatically deletes the webhook on closure.", icon = "icon.svg", documentationRef = "https://docs.apify.com/platform/integrations/camunda", inputDataClass = ApifyInboundProperties.class)
public class ApifyInboundExecutable implements WebhookConnectorExecutable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApifyInboundExecutable.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * Users can't subscribe to all event types at once, so we use all of them.
     */
    private static final List<String> EVENT_TYPES = List.of(
            "ACTOR.RUN.SUCCEEDED",
            "ACTOR.RUN.FAILED",
            "ACTOR.RUN.TIMED_OUT",
            "ACTOR.RUN.ABORTED");
    private static final String PAYLOAD_TEMPLATE = ApifyPayloadTemplate.getContent();
    private InboundConnectorContext context;
    private ApifyInboundProperties properties;
    private String callbackUrl;
    private String webhookId;
    private ApifyClient apifyClient;

    /**
     * Activates the inbound connector by creating a webhook subscription in Apify.
     * 
     * @param context The inbound connector context.
     * @throws Exception If webhook creation fails.
     */
    @Override
    public void activate(InboundConnectorContext context) throws Exception {
        LOGGER.debug("Activating Apify inbound connector.");
        this.context = context;

        try {
            this.properties = context.bindProperties(ApifyInboundProperties.class);
            this.callbackUrl = getCallbackUrl();

            // Validate callback URL format
            URLValidator.validateUrl(callbackUrl);

            this.apifyClient = new ApifyClient();

            // Resolve slug-based resource IDs (e.g., "username~actor-name") to actual IDs
            final var resolvedResourceId = resolveResourceId(properties.getNormalizedResourceId());

            // Create webhook in Apify
            createApifyWebhook(resolvedResourceId);
            LOGGER.info("Successfully created Apify webhook with webhook ID: {}.", webhookId);
            context.reportHealth(Health.up());
        } catch (Exception e) {
            LOGGER.error("Failed to activate Apify inbound connector: {}.", e.getMessage(), e);
            context.reportHealth(Health.down(e));
            closeApifyClient();
            throw e;
        }
    }

    /**
     * Closes the ApifyClient, logging any errors but not throwing them.
     */
    private void closeApifyClient() {
        if (apifyClient != null) {
            try {
                apifyClient.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close ApifyClient during cleanup: {}.", e.getMessage());
            } finally {
                apifyClient = null;
            }
        }
    }

    /**
     * Deactivates the inbound connector and deletes the webhook from Apify.
     * 
     * @throws Exception If webhook deletion fails.
     */
    @Override
    public void deactivate() throws Exception {
        if (webhookId != null && apifyClient != null && properties != null) {
            final var currentWebhookId = webhookId;
            webhookId = null;
            LOGGER.info("Deactivating Apify webhook with webhook ID: {}.", currentWebhookId);
            try {
                apifyClient.deleteWebhook(properties.authentication().token(), currentWebhookId);
                LOGGER.info("Successfully deleted Apify webhook with webhook ID: {}.", currentWebhookId);
            } catch (IOException e) {
                LOGGER.error("Failed to delete Apify webhook with webhook ID: {}: {}.", currentWebhookId, e.getMessage(), e);
                if (context != null) {
                    context.reportHealth(Health.down(e));
                }
            }
        }

        closeApifyClient();
    }

    /**
     * Processes incoming webhook requests from Apify.
     * 
     * @param payload The webhook payload containing headers and body.
     * @return WebhookResult indicating how to handle the webhook.
     * @throws Exception If processing fails.
     */
    @Override
    public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {
        LOGGER.debug("Received Apify webhook payload.");

        byte[] rawBody = payload.rawBody();

        if (rawBody == null || rawBody.length == 0) {
            LOGGER.warn("Received Apify webhook with empty body.");
            return createErrorResult(payload, "Received Apify webhook with empty body.", 400);
        }

        try {
            String bodyString = new String(rawBody, StandardCharsets.UTF_8);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(bodyString);

            ApifyInboundEvent event = OBJECT_MAPPER.treeToValue(jsonNode, ApifyInboundEvent.class);

            if (event == null) {
                LOGGER.warn("Failed to parse Apify webhook body as ApifyInboundEvent.");
                return createErrorResult(payload, "Failed to parse Apify webhook body as ApifyInboundEvent.", 400);
            }

            // Build the result map to pass to the process
            ApifyWebhookResponse connectorData = buildConnectorData(event);

            // Reuse the parsed JSON for MappedHttpRequest
            Object parsedBody = OBJECT_MAPPER.treeToValue(jsonNode, Object.class);
            MappedHttpRequest mappedRequest = new MappedHttpRequest(
                    parsedBody,
                    payload.headers(),
                    payload.params());

            // Return successful result with correlation data
            return createSuccessResult(mappedRequest, connectorData);

        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to parse Apify webhook body as ApifyInboundEvent.", e);
            return createErrorResult(payload, "Failed to parse Apify webhook body as ApifyInboundEvent.", 400);
        } catch (Exception e) {
            LOGGER.error("Failed to process Apify webhook as ApifyInboundEvent: {}.", e.getMessage(), e);
            return createErrorResult(payload, "Failed to process Apify webhook as ApifyInboundEvent: " + e.getMessage() + ".", 500);
        }
    }

    /**
     * Gets the base URL from the environment variables.
     * 
     * @return The base URL.
     */
    private String getBaseUrl() {
        String baseUrl = System.getenv("CONNECTOR_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "CONNECTOR_BASE_URL environment variable must be set for inbound connectors. \n"
                            + "This should be the public URL where Camunda connectors receive webhook callbacks.");
        }
        return baseUrl;
    }

    /**
     * Gets the callback URL from context properties.
     * Camunda does not provide a default programmatic api to retrieve full
     * redirect url
     * Connector runtime must know its own listening address
     * Camunda exposes just the path component via bpmn properties
     * 
     * @return The webhook callback URL, or null if not available
     * @throws IllegalArgumentException if the callback URL is not available
     */
    private String getCallbackUrl() {
        LOGGER.debug("Getting Apify webhook callback URL from Camunda runtime context.");

        // Get the inbound context from properties
        Object inboundObj = context.getProperties().get("inbound");
        Map<String, Object> inboundContext = inboundObj != null ? OBJECT_MAPPER.convertValue(
                inboundObj,
                new TypeReference<Map<String, Object>>() {
                }) : null;
        if (inboundContext == null) {
            throw new IllegalStateException("Inbound context configuration is missing from BPMN properties");
        }

        String contextValue = (String) inboundContext.get("context");
        if (contextValue == null) {
            throw new IllegalStateException("Inbound context path is not configured in BPMN element");
        }

        String baseUrl = getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        if (contextValue.startsWith("/")) {
            contextValue = contextValue.substring(1);
        }

        return baseUrl + "/inbound/" + contextValue;
    }

    /**
     * Creates a webhook subscription in Apify for the configured resource.
     * 
     * @param resolvedResourceId The resolved Apify resource ID.
     * @throws IOException If the webhook creation fails.
     */
    private void createApifyWebhook(String resolvedResourceId) throws IOException {
        LOGGER.debug("Creating Apify webhook with callback URL: {}.", callbackUrl);

        String webhookJson = buildWebhookPayload(resolvedResourceId);

        // Create the webhook
        ApifyClient.ResponseResult result = apifyClient.createWebhook(properties.authentication().token(), webhookJson);
        String responseBody = result.getResponseBody();
        JsonNode responseNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode dataNode = responseNode.path("data");

        // If there is no data node, use the response
        if (dataNode.isMissingNode()) {
            dataNode = responseNode;
        }

        // Extract webhook ID from response
        if (dataNode.has("id")) {
            webhookId = dataNode.get("id").asText();
        } else {
            throw new IOException("Failed to extract webhook ID from response: " + responseBody);
        }
    }

    /**
     * Resolves a resource identifier to its actual Apify resource ID.
     * If the identifier contains a tilde (~), it is a slug (e.g., "username~actor-name")
     * and needs to be resolved via the Apify API. Otherwise, it is already an ID.
     *
     * @param normalizedResourceId The normalized resource ID (with ~ instead of /)
     * @return The actual Apify resource ID
     * @throws IOException              if the API call fails or the response cannot be parsed
     * @throws IllegalArgumentException if the resource ID is null or empty
     */
    private String resolveResourceId(String normalizedResourceId) throws IOException {
        if (normalizedResourceId == null || normalizedResourceId.isBlank()) {
            throw new IllegalArgumentException("Resource ID must not be null or empty.");
        }

        if (!normalizedResourceId.contains("~")) {
            LOGGER.debug("Resource ID '{}' does not contain '~', using as-is.", normalizedResourceId);
            return normalizedResourceId;
        }

        LOGGER.debug("Resource ID '{}' contains '~', resolving via Apify API.", normalizedResourceId);
        final var authToken = properties.authentication().token();
        final var result = switch (properties.resourceType()) {
            case ACTOR -> apifyClient.getActor(normalizedResourceId, authToken);
            case TASK -> apifyClient.getTask(normalizedResourceId, authToken);
        };

        // Check the status code of the API response
        final int statusCode = result.getStatusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException(String.format(
                    "Failed to resolve resource ID '%s': Apify API returned HTTP %d.",
                    normalizedResourceId, statusCode));
        }

        final var responseBody = result.getResponseBody();
        final var responseNode = OBJECT_MAPPER.readTree(responseBody);
        final var idNode = responseNode.path("data").path("id");

        if (idNode.isMissingNode() || idNode.isNull()) {
            final var truncatedBody = responseBody.length() > 500
                    ? responseBody.substring(0, 500) + "..." : responseBody;
            throw new IOException("Failed to resolve resource ID from API response: " + truncatedBody);
        }

        final var resolvedId = idNode.asText();
        LOGGER.info("Resolved resource '{}' to ID '{}'.", normalizedResourceId, resolvedId);
        return resolvedId;
    }

    /**
     * Builds the JSON payload for creating an Apify webhook.
     * 
     * @param resolvedResourceId The resolved Apify resource ID.
     * @return The JSON payload for creating an Apify webhook.
     * @throws JsonProcessingException If the JSON payload cannot be created.
     */
    private String buildWebhookPayload(String resolvedResourceId) throws JsonProcessingException {
        LOGGER.debug("Building Apify webhook payload with callback URL: {}.", callbackUrl);
        ObjectNode webhookNode = OBJECT_MAPPER.createObjectNode();

        // Add all event types to the webhook
        ArrayNode eventTypesArray = webhookNode.putArray("eventTypes");
        for (String eventType : EVENT_TYPES) {
            eventTypesArray.add(eventType);
        }

        // Set the condition based on resource type
        ObjectNode conditionNode = OBJECT_MAPPER.createObjectNode();
        conditionNode.put(properties.resourceType().getConditionKey(), resolvedResourceId);
        webhookNode.set("condition", conditionNode);
        webhookNode.put("requestUrl", callbackUrl);
        webhookNode.put("payloadTemplate", PAYLOAD_TEMPLATE);
        webhookNode.put("shouldInterpolateStrings", true);
        webhookNode.put("idempotencyKey", generateIdempotencyKey(callbackUrl, resolvedResourceId));

        return OBJECT_MAPPER.writeValueAsString(webhookNode);
    }

    /**
     * Generates a SHA-256 hash to use as the idempotency key for webhook creation.
     * <p>
     * Package-private for unit testing.
     *
     * @param callbackUrl The webhook callback URL.
     * @param resourceId  The resolved Apify resource ID.
     * @return A hex-encoded SHA-256 hash string.
     */
    static String generateIdempotencyKey(String callbackUrl, String resourceId) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var input = (callbackUrl + ":" + resourceId).getBytes(StandardCharsets.UTF_8);
            final var hash = digest.digest(input);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Builds the connector data map from the Apify event.
     * 
     * @param event The Apify event to build the connector data from.
     * @return The connector data map.
     */
    private ApifyWebhookResponse buildConnectorData(ApifyInboundEvent event) {
        LOGGER.debug("Building connector data map from Apify event.");
        return ApifyWebhookResponse.fromEvent(event);
    }

    /**
     * Creates a successful WebhookResult.
     * 
     * @param request       The MappedHttpRequest object.
     * @param connectorData The connector data map.
     * @return The WebhookResult object.
     */
    private WebhookResult createSuccessResult(MappedHttpRequest request, ApifyWebhookResponse connectorData) {
        LOGGER.debug("Creating successful WebhookResult.");
        return new SuccessWebhookResult(request,
                OBJECT_MAPPER.convertValue(connectorData, new TypeReference<Map<String, Object>>() {
                }));
    }

    /**
     * Creates an error WebhookResult.
     * 
     * @param payload      The WebhookProcessingPayload object.
     * @param errorMessage The error message.
     * @return The WebhookResult object.
     */
    private WebhookResult createErrorResult(WebhookProcessingPayload payload, String errorMessage, int statusCode) {
        LOGGER.debug("Creating error WebhookResult with error message: {}.", errorMessage);
        MappedHttpRequest request = new MappedHttpRequest(
                null,
                payload.headers(),
                payload.params());

        return new ErrorWebhookResult(request, errorMessage, statusCode);
    }

    /**
     * Record-based implementation of WebhookResult for successful webhook
     * processing.
     * Returns HTTP 200 with a success status.
     * 
     * @param request       The MappedHttpRequest object.
     * @param connectorData The connector data map.
     * @return The WebhookResult object.
     */
    private record SuccessWebhookResult(
            MappedHttpRequest request,
            Map<String, Object> connectorData) implements WebhookResult {

        @Override
        public Function<WebhookResultContext, WebhookHttpResponse> response() {
            return ctx -> new WebhookHttpResponse(
                    Map.of("status", "ok"),
                    Map.of("Content-Type", "application/json"),
                    200);
        }
    }

    /**
     * Record-based implementation of WebhookResult for error cases.
     * Returns error HTTP status code and error message.
     * 
     * @param request      The MappedHttpRequest object.
     * @param errorMessage The error message.
     * @return The WebhookResult object.
     */
    private record ErrorWebhookResult(
            MappedHttpRequest request,
            String errorMessage, int statusCode) implements WebhookResult {

        @Override
        public Map<String, Object> connectorData() {
            return Map.of("error", errorMessage);
        }

        @Override
        public Function<WebhookResultContext, WebhookHttpResponse> response() {
            return ctx -> new WebhookHttpResponse(
                    Map.of("error", errorMessage),
                    Map.of("Content-Type", "application/json"),
                    statusCode);
        }
    }
}
