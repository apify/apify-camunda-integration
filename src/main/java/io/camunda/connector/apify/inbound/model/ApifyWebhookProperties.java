package io.camunda.connector.apify.inbound.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record ApifyWebhookProperties(
    @TemplateProperty(
            id = "context",
            label = "Webhook ID",
            group = "endpoint",
            description = "The webhook ID is a part of the URL endpoint",
            feel = Property.FeelMode.disabled)
        @NotBlank
        String context ) {
  public ApifyWebhookProperties(ApifyConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context);
  }

  public record ApifyConnectorPropertiesWrapper(ApifyWebhookProperties inbound) {}

  @Override
  public String toString() {
    return "ApifyWebhookProperties{"
        + "context='"
        + context
        + "'"
        + "}";
  }
}
