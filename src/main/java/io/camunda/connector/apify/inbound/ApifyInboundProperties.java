package io.camunda.connector.apify.inbound;

import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.inbound.dto.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for the Apify Inbound Connector.
 * These properties are bound from the BPMN element template configuration.
 *
 * <p>Note: The element template is manually defined in
 * {@code element-templates/apify-connector-*.json} so we don't use
 * {@code @TemplateProperty} annotations here to avoid duplication.
 */
public record ApifyInboundProperties(
        /**
         * Authentication credentials for the Apify API.
         */
        @Valid @NotNull Authentication authentication,

        /**
         * The type of resource to subscribe to (actor or task).
         */
        @NotNull ResourceType resourceType,

        /**
         * The Actor ID or Task ID to subscribe to.
         */
        @NotEmpty String resourceId,

        /**
         * Public base URL of the connector runtime. Used to build the webhook
         * callback URL sent to Apify. Required because the Camunda SDK does not
         * expose the runtime's public address.
         */
        @NotEmpty String connectorWebhookUrl) {

    /**
     * Normalizes resource ID by replacing forward slash (/) with tilde (~).
     * This is required by Apify when using username/resourceName format.
     *
     * @return The normalized resource ID
     */
    public String getNormalizedResourceId() {
        return resourceId.replace("/", "~");
    }

    @Override
    public String toString() {
        String tokenDisplay = authentication != null && authentication.token() != null
                && !authentication.token().isEmpty() ? "****" : "null";
        return "ApifyInboundProperties[" +
                "authentication.token='" + tokenDisplay + '\'' +
                ", resourceType=" + resourceType +
                ", resourceId='" + resourceId + '\'' +
                ", connectorWebhookUrl='" + connectorWebhookUrl + '\'' +
                ']';
    }
}
