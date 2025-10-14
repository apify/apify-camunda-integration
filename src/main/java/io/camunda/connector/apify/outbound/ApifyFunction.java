package io.camunda.connector.apify.outbound;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsInput;
// import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@OutboundConnector(
    name = "APIFY",
    inputVariables = {
        "authentication",
        "operation",
        "apifyRequestInput",
        "apifyRequestInput.runActorInput",
        "apifyRequestInput.runTaskInput",
        "apifyRequestInput.getDatasetItemsInput"
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

    // Handle different operation types
    switch (operationType) {
      case "runActor":
        return handleRunActor(authentication, apifyRequestInput);
      case "runTask":
        return handleRunTask(authentication, apifyRequestInput);
      case "getDatasetItems":
        return handleGetDatasetItems(authentication, apifyRequestInput);
      default:
        return new ApifyResult("Unsupported operation type: " + operationType);
    }
  }

  private ApifyResult handleRunActor(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runActor operation");
    // TODO: Implement runActor logic
    return new ApifyResult("RunActor operation - Actor ID: " + apifyRequestInput.runActorInput().actorId());
  }

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runTask operation");
    // TODO: Implement runTask logic
    return new ApifyResult("RunTask operation - Task ID: " + apifyRequestInput.runTaskInput().taskId());
  }

  private ApifyResult handleGetDatasetItems(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    GetDatasetItemsInput datasetInput = apifyRequestInput.getDatasetItemsInput();
    
    if (datasetInput == null) {
      return new ApifyResult("Error: getDatasetItemsInput is null");
    }
    
    if (authentication == null || authentication.token() == null || authentication.token().isEmpty()) {
      return new ApifyResult("Error: Authentication token is required");
    }
    
    try (ApifyClient apifyClient = new ApifyClient()) {
      
      String datasetItems = apifyClient.getDatasetItems(
        datasetInput.datasetId(),
        authentication.token(),
        datasetInput.offset(),
        datasetInput.limit()
      );
      
      return new ApifyResult(datasetItems);
      
    } catch (IOException e) {
      LOGGER.error("Failed to get dataset items: {}", e.getMessage(), e);
      return new ApifyResult("Error: Failed to get dataset items - " + e.getMessage());
    }
  }
}
