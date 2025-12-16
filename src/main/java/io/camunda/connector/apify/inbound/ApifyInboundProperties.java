package io.camunda.connector.apify.inbound;

import io.camunda.connector.apify.inbound.dto.ResourceType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for the Apify Inbound Connector.
 * These properties are bound from the BPMN element template configuration.
 * 
 * Note: The element template is manually defined in
 * element-templates/apify-inbound-connector.json
 * so we don't use @TemplateProperty annotations here to avoid duplication.
 */
public record ApifyInboundProperties(
        /**
         * The Apify API token for authentication.
         */
        @NotEmpty String token,

        /**
         * The type of resource to subscribe to (actor or task).
         */
        @NotNull ResourceType resourceType,

        /**
         * The Actor ID or Task ID to subscribe to.
         */
        @NotEmpty String resourceId) {

    /**
     * Normalizes resource ID by replacing forward slash (/) with tilde (~).
     * This is required by Apify when using username/resourceName format.
     * 
     * @return The normalized resource ID
     */
    public String getNormalizedResourceId() {
        return resourceId.replace("/", "~");
    }
}
