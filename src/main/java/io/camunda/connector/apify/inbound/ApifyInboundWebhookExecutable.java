package io.camunda.connector.apify.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.apify.inbound.model.ApifyWebhookProperties;
import io.camunda.connector.apify.inbound.model.ApifyWebhookProperties.ApifyConnectorPropertiesWrapper;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InboundConnector(name = "Apify Inbound", type = "io.camunda:apify-webhook:1")
@ElementTemplate(
    id = "io.camunda.connectors.inbound.Apify.v1",
    name = "Apify Webhook Boundary Event Connector",
    icon = "icon.svg",
    version = 7,
    description = "Receive webhook events from Apify",
    inputDataClass = ApifyConnectorPropertiesWrapper.class,
    // documentationRef =
    //     "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors",
    propertyGroups = {@PropertyGroup(id = "endpoint", label = "Webhook configuration")},
    elementTypes = {
      @ConnectorElementType(
          appliesTo = BpmnType.START_EVENT,
          elementType = BpmnType.MESSAGE_START_EVENT,
          templateIdOverride = "io.camunda.connectors.inbound.Apify.MessageStartEvent.v1",
          templateNameOverride = "Apify Webhook Message Start Event Connector"),
    })
public class ApifyInboundWebhookExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApifyInboundWebhookExecutable.class);


  private ApifyWebhookProperties props;
  private InboundConnectorContext context;

  public ApifyInboundWebhookExecutable() {
    // Default constructor
  }

  @Override
  public void activate(InboundConnectorContext connectorContext) {
    this.context = connectorContext;
    var wrapperProps = context.bindProperties(ApifyConnectorPropertiesWrapper.class);
    props = new ApifyWebhookProperties(wrapperProps);
    
    // The context property contains the webhook ID that becomes part of the URL
    String webhookContext = props.context(); // This is the "context" from your properties
    
    // You can now construct the full webhook URL
    String webhookUrl = constructWebhookUrl(webhookContext);
    
    // context.reportHealth(Health.up());

    this.context = connectorContext;
    LOGGER.info("Activating Apify Inbound Webhook Connector");
    LOGGER.debug("Activation context: {}", connectorContext);
    LOGGER.debug("Webhook URL: {}", webhookUrl);
    // TODO: Implement activation logic
  }

  public void onWebhookEvent(WebhookProcessingPayload webhookProcessingPayload) {
    LOGGER.info("Processing Apify webhook event");
    LOGGER.debug("Webhook method: {}, URL: {}", 
        webhookProcessingPayload.method(), 
        webhookProcessingPayload.requestURL());
    LOGGER.debug("Webhook headers: {}", webhookProcessingPayload.headers());
    LOGGER.debug("Webhook params: {}", webhookProcessingPayload.params());
    LOGGER.debug("Webhook raw body size: {} bytes", webhookProcessingPayload.rawBody().length);


    // TODO: Implement webhook event handling logic
  }
  
  @Override
  public void deactivate() {
    LOGGER.info("Deactivating Apify Inbound Webhook Connector");
    // TODO: Implement deactivation logic
  }


  private String constructWebhookUrl(String context) {
    // TODO: Implement base URL construction
    String baseUrl = "http://localhost:8080";
    return baseUrl + "/inbound/" + context;
}
}
