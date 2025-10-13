package io.camunda.connector.apify.inbound.model;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import java.util.Map;
import java.util.function.Function;

public class ApifyWebhookProcessingResult implements WebhookResult {

  private final MappedHttpRequest request;
  private final Map<String, Object> connectorData;
  private final WebhookHttpResponse response;

  public ApifyWebhookProcessingResult(
      MappedHttpRequest request, Map<String, Object> connectorData, WebhookHttpResponse response) {
    this.request = request;
    this.connectorData = connectorData;
    this.response = response;
  }

  @Override
  public MappedHttpRequest request() {
    return request;
  }

  @Override
  public Map<String, Object> connectorData() {
    return connectorData;
  }

  @Override
  public Function<WebhookResultContext, WebhookHttpResponse> response() {
    return (c) -> response;
  }

  public WebhookHttpResponse getResponse() {
    return response;
  }
}
