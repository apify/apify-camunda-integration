package io.camunda.connector.apify.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
// import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "APIFY",
    inputVariables = {
        "authentication",
        "operation",
        "apifyRequestInput",
        "apifyRequestInput.runActorInput",
        "apifyRequestInput.runTaskInput"
    },
    type = "io.camunda:apify-outbound:1")
@ElementTemplate(
    id = "io.camunda.connector.outbound.Apify.v1",
    name = "Apify Connector",
    version = 1,
    description = "Access Apify tools for web scraping, data extraction, and automation.",
    // TODO: update documentation link
    documentationRef = "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/",
    inputDataClass = ApifyRequest.class)
public class ApifyFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApifyFunction.class);

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var connectorRequest = context.bindVariables(ApifyRequest.class);
    return executeConnector(connectorRequest);
  }

  private ApifyResult executeConnector(final ApifyRequest connectorRequest) {
    LOGGER.info("Executing my connector with request {}", connectorRequest);
    String operationType = connectorRequest.operation().type();

    Authentication authentication = connectorRequest.authentication();
    ApifyRequestInput apifyRequestInput = connectorRequest.apifyRequestInput();

    LOGGER.info("Authentication {}", authentication);
    LOGGER.info("Apify Request Input {}", apifyRequestInput);


    // if (message != null && message.toLowerCase().startsWith("fail")) {
    //   throw new ConnectorException("FAIL", "My property started with 'fail', was: " + message);
    // }
    return new ApifyResult("Operation type: " + operationType + " Authentication: " + authentication + " Apify Request Input: " + apifyRequestInput);
  }
}
