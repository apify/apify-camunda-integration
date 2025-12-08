package io.camunda.connector.apify.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.annotation.InboundConnector;
// TODO: check if this is needed
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Apify Inbound Connector implementation that listens for Apify webhook events.
 */
@InboundConnector(
    name = "Apify Inbound Connector",
    type = "io.camunda:apify-inbound:1"
)
@ElementTemplate(
    id = "io.camunda.connector.inbound.Apify.v1",
    name = "Apify Connector",
    version = 1,
    description = "Creates an Apify webhook for completed Actor or Task runs, receives event updates, and automatically deletes the webhook on closure.",
    // TODO: update documentation link
    documentationRef = "https://docs.camunda.io/docs/components/connectors/in-the-box-connectors/available-connectors-overview/",
    inputDataClass = ApifyInboundProperties.class
)
public class ApifyInboundExecutable implements WebhookConnectorExecutable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApifyInboundExecutable.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> EVENT_TYPES = List.of(
        "ACTOR.RUN.SUCCEEDED",
        "ACTOR.RUN.FAILED",
        "ACTOR.RUN.TIMED_OUT",
        "ACTOR.RUN.ABORTED"
    );
    private static final String PAYLOAD_TEMPLATE = """
        {
            "userId": {{userId}},
            "createdAt": {{createdAt}},
            "eventType": {{eventType}},
            "eventData": {{eventData}},
            "resource": {{resource}}
        }
    """;
    private InboundConnectorContext context;
    private ApifyInboundProperties properties;
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
        LOGGER.debug("Activating Apify inbound connector");
        this.context = context;
        this.properties = context.bindProperties(ApifyInboundProperties.class);
        this.apifyClient = new ApifyClient();
        
        LOGGER.info("Creating Apify webhook for resource type: {}, resource ID: {}", 
            properties.resourceType(), properties.resourceId());
        
        // TODO: i should check how the camunda generates the callback url 
        String callbackUrl = getCallbackUrl();
        
        if (callbackUrl != null && !callbackUrl.isEmpty()) {
            // Create webhook in Apify
            createApifyWebhook(callbackUrl);
            LOGGER.info("Apify webhook created successfully. Webhook ID: {}", webhookId);
            context.reportHealth(Health.up());
        } else {
            LOGGER.warn("No callback URL available. Webhook not created. " +
                "Configure the webhook manually in the Apify Console, pointing to this connector's endpoint. The expected callback URL is: {}", callbackUrl);
            context.reportHealth(Health.down());
        }
    }
    
    /**
     * Deactivates the inbound connector and deletes the webhook from Apify.
     * 
     * @throws Exception if webhook deletion fails
     */
    @Override
    public void deactivate() throws Exception {
        LOGGER.info("Removing Apify webhook. Webhook ID: {}", webhookId);
        
        if (webhookId != null && apifyClient != null && properties != null) {
            try {
                apifyClient.deleteWebhook(properties.token(), webhookId);
                LOGGER.info("Webhook {} deleted successfully", webhookId);
            } catch (IOException e) {
                LOGGER.error("Failed to delete webhook {}: {}", webhookId, e.getMessage(), e);
            }
        }
        
        if (apifyClient != null) {
            try {
                apifyClient.close();
                context.reportHealth(Health.up());
            } catch (IOException e) {
                LOGGER.warn("Error closing ApifyClient: {}", e.getMessage());
                context.reportHealth(Health.down());
            }
        }
        
        LOGGER.info("Apify webhook removed. Webhook ID: {}", webhookId);
    }
    
    /**
     * Processes incoming webhook requests from Apify.
     * 
     * @param payload The webhook payload containing headers and body
     * @return WebhookResult indicating how to handle the webhook
     * @throws Exception if processing fails
     */
    @Override
    public WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception {
        // TODO: I should check if this works and how to test it on my local machine
        // TODO: but tbh this can be tested by using either curl or ngrok (making a callback url public)
        LOGGER.debug("Received webhook payload");
        
        byte[] rawBody = payload.rawBody();
        
        if (rawBody == null || rawBody.length == 0) {
            LOGGER.warn("Received webhook with empty body");
            return createErrorResult(payload, "Empty request body");
        }
        
        try {
            // Parse the webhook body
            String bodyString = new String(rawBody, StandardCharsets.UTF_8);
            ApifyInboundEvent event = OBJECT_MAPPER.readValue(bodyString, ApifyInboundEvent.class);
            
            if (event == null) {
                LOGGER.warn("Failed to parse webhook body");
                return createErrorResult(payload, "Failed to parse webhook body");
            }
            
            // Build the result map to pass to the process
            Map<String, Object> connectorData = buildConnectorData(event);
            
            // Create MappedHttpRequest from payload
            Object parsedBody = OBJECT_MAPPER.readValue(bodyString, Object.class);
            MappedHttpRequest mappedRequest = new MappedHttpRequest(
                parsedBody,
                payload.headers(),
                payload.params()
            );
            
            // Return successful result with correlation data
            return createSuccessResult(mappedRequest, connectorData);
            
        } catch (Exception e) {
            LOGGER.error("Error processing webhook: {}", e.getMessage(), e);
            return createErrorResult(payload, "Error processing webhook: " + e.getMessage());
        }
    }
    
    /**
     * Gets the callback URL from context properties.
     * 
     * @return The webhook callback URL, or null if not available
     */
    private String getCallbackUrl() {
        // TODO: the callback url is http://localhost:9898/inbound/<webhook_id>
        // TODO: it's worth to check how to get the url from context, the webhook_id is then
        // TODO: stored in the context, as it is generated by camunda
        LOGGER.debug("Getting callback URL from Camunda runtime context");
        
        // // Get the callback URL from context properties
        // // The Camunda runtime provides this through the inbound connector context
        // Map<String, Object> contextProperties = context.getProperties();
        
        // Object webhookUrl = contextProperties.get("inbound.connector.url");
        // if (webhookUrl != null) {
        //     String url = webhookUrl.toString();
        //     LOGGER.info("Retrieved webhook callback URL from context: {}", url);
        //     return url;
        // }
        
        // // Fallback: try alternative property names
        // webhookUrl = contextProperties.get("webhookUrl");
        // if (webhookUrl != null) {
        //     String url = webhookUrl.toString();
        //     LOGGER.info("Retrieved webhook callback URL from context (fallback): {}", url);
        //     return url;
        // }
        
        // // If no automatic URL is available, log a warning
        // // This might happen in local testing or if the runtime doesn't provide the URL automatically
        // LOGGER.warn("No automatic callback URL available from Camunda runtime. " +
        //     "Webhook must be configured manually in Apify Console. " +
        //     "Available context properties: {}", contextProperties.keySet());
        
        // TODO: just for testing
        return "http://localhost:9898/inbound/" + properties.context();
    }
    
    /**
     * Creates a webhook subscription in Apify for the configured resource.
     * 
     * @param callbackUrl The callback URL to create the webhook for.
     * @throws IOException If the webhook creation fails.
     */
    private void createApifyWebhook(String callbackUrl) throws IOException {
        LOGGER.debug("Creating Apify webhook with callback URL: {}", callbackUrl);
        
        // Build the webhook payload
        String webhookJson = buildWebhookPayload(callbackUrl);
        
        // Create the webhook
        ApifyClient.ResponseResult result = apifyClient.createWebhook(properties.token(), webhookJson);
        String responseBody = result.getResponseBody();
        
        // Extract webhook ID from response
        JsonNode responseNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode dataNode = responseNode.path("data");
        if (dataNode.isMissingNode()) {
            dataNode = responseNode;
        }
        
        if (dataNode.has("id")) {
            webhookId = dataNode.get("id").asText();
            LOGGER.info("Created Apify webhook with ID: {}", webhookId);
        } else {
            throw new IOException("Failed to extract webhook ID from response: " + responseBody);
        }
    }
    
    /**
     * Builds the JSON payload for creating an Apify webhook.
     * 
     * @param callbackUrl The callback URL to create the webhook for.
     * @return The JSON payload for creating an Apify webhook.
     * @throws JsonProcessingException If the JSON payload cannot be created.
     */
    private String buildWebhookPayload(String callbackUrl) throws JsonProcessingException {
        LOGGER.debug("Building Apify webhook payload with callback URL: {}", callbackUrl);
        ObjectNode webhookNode = OBJECT_MAPPER.createObjectNode();
        
        ArrayNode eventTypesArray = webhookNode.putArray("eventTypes");
        for (String eventType : EVENT_TYPES) {
            eventTypesArray.add(eventType);
        }
        
        // Set the callback URL
        webhookNode.put("requestUrl", callbackUrl);
        
        // Set the condition based on resource type
        ObjectNode conditionNode = OBJECT_MAPPER.createObjectNode();
        if (properties.resourceType().equals("actor")) {
            conditionNode.put("actorId", properties.getNormalizedResourceId());
        } else {
            conditionNode.put("actorTaskId", properties.getNormalizedResourceId());
        }
        webhookNode.set("condition", conditionNode);
        
        webhookNode.put("payloadTemplate", PAYLOAD_TEMPLATE);
        
        // Generate idempotency key to prevent duplicate webhooks
        String idempotencyKey = generateIdempotencyKey();
        webhookNode.put("idempotencyKey", idempotencyKey);
        
        // Set additional options
        webhookNode.put("shouldInterpolateStrings", true);
        
        return OBJECT_MAPPER.writeValueAsString(webhookNode);
    }
    
    /**
     * Generates a unique idempotency key for the webhook.
     * 
     * @return The idempotency key.
     */
    private String generateIdempotencyKey() {
        // TODO: I should check if this is needed
        // TODO: or if there is a better way to do it
        LOGGER.debug("Generating idempotency key");
        // Use process info and resource ID to create a stable key
        List<ProcessElement> elements = context.getDefinition().elements();
        String processId = "unknown";
        String elementId = "unknown";
        
        if (elements != null && !elements.isEmpty()) {
            ProcessElement element = elements.get(0);
            processId = element.bpmnProcessId();
            elementId = element.elementId();
        }
        
        String resourceId = properties.resourceId();
        
        return String.format("camunda-%s-%s-%s", processId, elementId, resourceId)
            .replaceAll("[^a-zA-Z0-9-]", "-");
    }
    
    /**
     * Builds the connector data map from the Apify event.
     * 
     * @param event The Apify event to build the connector data from.
     * @return The connector data map.
     */
    private Map<String, Object> buildConnectorData(ApifyInboundEvent event) {
        // TODO: I should check if this is needed
        LOGGER.debug("Building connector data map from Apify event");
        Map<String, Object> result = new HashMap<>();
        
        result.put("eventType", event.eventType());
        result.put("userId", event.userId());
        result.put("createdAt", event.createdAt());
        result.put("runId", event.getRunId());
        result.put("status", event.getStatus());
        result.put("actorId", event.getActorId());
        result.put("taskId", event.getTaskId());
        result.put("defaultDatasetId", event.getDefaultDatasetId());
        result.put("defaultKeyValueStoreId", event.getDefaultKeyValueStoreId());
        
        // Include the full resource as a map
        if (event.resource() != null) {
            result.put("resource", OBJECT_MAPPER.convertValue(event.resource(), Map.class));
        }
        
        // Include event data if present
        if (event.eventData() != null) {
            result.put("eventData", OBJECT_MAPPER.convertValue(event.eventData(), Map.class));
        }
        
        return result;
    }
    
    /**
     * Creates a successful WebhookResult.
     * 
     * @param request The MappedHttpRequest object.
     * @param connectorData The connector data map.
     * @return The WebhookResult object.
     */
    private WebhookResult createSuccessResult(MappedHttpRequest request, Map<String, Object> connectorData) {
        LOGGER.debug("Creating successful WebhookResult");
        return new SuccessWebhookResult(request, connectorData);
    }
    
    /**
     * Creates an error WebhookResult.
     * 
     * @param payload The WebhookProcessingPayload object.
     * @param errorMessage The error message.
     * @return The WebhookResult object.
     */
    private WebhookResult createErrorResult(WebhookProcessingPayload payload, String errorMessage) {
        LOGGER.debug("Creating error WebhookResult with error message: {}", errorMessage);
        MappedHttpRequest request = new MappedHttpRequest(
            null,
            payload.headers(),
            payload.params()
        );
        
        return new ErrorWebhookResult(request, errorMessage);
    }
    
    /**
     * Record-based implementation of WebhookResult for successful webhook processing.
     * Returns HTTP 200 with a success status.
     * 
     * @param request The MappedHttpRequest object.
     * @param connectorData The connector data map.
     * @return The WebhookResult object.
     */
    private record SuccessWebhookResult(
        MappedHttpRequest request,
        Map<String, Object> connectorData
    ) implements WebhookResult {
        
        @Override
        public Function<WebhookResultContext, WebhookHttpResponse> response() {
            return ctx -> new WebhookHttpResponse(
                Map.of("status", "ok"),
                Map.of("Content-Type", "application/json"),
                200
            );
        }
    }
    
    /**
     * Record-based implementation of WebhookResult for error cases.
     * Returns HTTP 400 with an error message.
     * 
     * @param request The MappedHttpRequest object.
     * @param errorMessage The error message.
     * @return The WebhookResult object.
     */
    private record ErrorWebhookResult(
        MappedHttpRequest request,
        String errorMessage
    ) implements WebhookResult {
        
        @Override
        public Map<String, Object> connectorData() {
            return Map.of("error", errorMessage);
        }
        
        @Override
        public Function<WebhookResultContext, WebhookHttpResponse> response() {
            return ctx -> new WebhookHttpResponse(
                Map.of("error", errorMessage),
                Map.of("Content-Type", "application/json"),
                400
            );
        }
    }
}
