package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.common.RunOptions;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsInput;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsResponse;
import io.camunda.connector.apify.outbound.dto.RunActorResponse;
import io.camunda.connector.apify.outbound.dto.RunTaskResponse;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var connectorRequest = context.bindVariables(ApifyRequest.class);
    return executeConnector(connectorRequest);
  }

  private ApifyResult executeConnector(final ApifyRequest connectorRequest) {
    String operationType = connectorRequest.operation().type();

    Authentication authentication = connectorRequest.authentication();
    ApifyRequestInput apifyRequestInput = connectorRequest.apifyRequestInput();

    LOGGER.info("Operation Type {}", operationType);
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
        throw new ConnectorInputException("Unsupported operation type: " + operationType);
    }
  }

  private ApifyResult handleRunActor(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    if (apifyRequestInput == null || apifyRequestInput.runActorInput() == null) {
      throw new ConnectorInputException("Error: runActorInput is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.runActorInput();

    try (ApifyClient apifyClient = new ApifyClient()) {
      // Get actor details to validate and get build info
      String actorResponse = apifyClient.getActor(input.actorId(), authentication.token());
      if (actorResponse == null || actorResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Actor not found - " + input.actorId());
      }

      // Get build information (either specified build or default)
      String buildResponse;
      if (input.build() != null && !input.build().trim().isEmpty()) {
        // Get build by tag from actor's taggedBuilds
        String buildId = extractBuildIdFromTag(actorResponse, input.build());
        if (buildId == null) {
          throw new RuntimeException("Error: Build tag '" + input.build() + "' not found for actor " + input.actorId());
        }
        buildResponse = apifyClient.getBuild(buildId, authentication.token());
      } else {
        // Get default build
        buildResponse = apifyClient.getDefaultBuild(input.actorId(), authentication.token());
      }

      if (buildResponse == null || buildResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Build not found for actor " + input.actorId());
      }

      // Extract default input values from build definition
      Map<String, Object> defaultInput = extractDefaultInputFromBuild(buildResponse);
      
      // Parse user input JSON (convert JsonNode to Map)
      Map<String, Object> userInput = new HashMap<>();
      if (input.inputJson() != null && !input.inputJson().isNull()) {
        JsonNode inputJsonNode = input.inputJson();
        // If it's a string node, parse it first
        if (inputJsonNode.isTextual()) {
          try {
            inputJsonNode = objectMapper.readTree(inputJsonNode.asText());
          } catch (Exception e) {
            LOGGER.warn("Failed to parse inputJson as JSON string: {}", e.getMessage());
          }
        }
        userInput = convertJsonNodeToMap(inputJsonNode);
      }
      
      // Merge default input with user input (user input takes precedence)
      Map<String, Object> mergedInput = new HashMap<>(defaultInput);
        mergedInput.putAll(userInput);
      
      // Convert merged input back to JSON
      String mergedInputJson = mapToJson(mergedInput);
      
      // Run the actor with merged input and parameters
      var runOptions = new RunOptions(
        input.timeout(),
        input.memory(),
        input.build(),
        null // Don't wait for finish initially
      );
      String response = apifyClient.runActor(
        authentication.token(),
        input.actorId(),
        mergedInputJson,
        runOptions
      );
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response, authentication.token());
      }
      
      return new RunActorResponse(response);
    } catch (IOException e) {
      LOGGER.error("Failed to run actor: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to run actor - " + e.getMessage(), e);
    }
  }

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runTask operation");
    if (apifyRequestInput == null || apifyRequestInput.runTaskInput() == null) {
      throw new ConnectorInputException("Error: runTaskInput is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.runTaskInput();

    try (ApifyClient apifyClient = new ApifyClient()) {
      // Check if task exists
      String taskResponse = apifyClient.getTask(input.taskId(), authentication.token());
      if (taskResponse == null || taskResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Task not found - " + input.taskId());
      }

      // Use input JSON if provided, otherwise use task's default input
      String inputJson = null;
      if (input.inputJson() != null && !input.inputJson().isNull()) {
        JsonNode inputJsonNode = input.inputJson();
        // If it's a string node, use it directly; otherwise serialize the node
        if (inputJsonNode.isTextual()) {
          inputJson = inputJsonNode.asText();
        } else {
          inputJson = objectMapper.writeValueAsString(inputJsonNode);
        }
      }
      
      // Run the task with parameters
      var runOptions = new RunOptions(
        input.timeout(),
        input.memory(),
        input.build(),
        null // Don't wait for finish initially
      );
      String response = apifyClient.runTask(
        authentication.token(),
        input.taskId(),
        inputJson,
        runOptions
      );
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response, authentication.token());
      }
      
      return new RunTaskResponse(response);
    } catch (IOException e) {
      LOGGER.error("Failed to run task: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to run task - " + e.getMessage(), e);
    } catch (Exception e) {
      LOGGER.error("Failed to run task: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to run task - " + e.getMessage(), e);
    }
  }

  private ApifyResult handleGetDatasetItems(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    GetDatasetItemsInput datasetInput = apifyRequestInput.getDatasetItemsInput();
    
    if (datasetInput == null) {
      throw new ConnectorInputException("Error: getDatasetItemsInput is null");
    }
    
    validateAuthentication(authentication);
    
    try (ApifyClient apifyClient = new ApifyClient()) {
      
      String datasetItems = apifyClient.getDatasetItems(
        datasetInput.datasetId(),
        authentication.token(),
        datasetInput.offset(),
        datasetInput.limit()
      );
      
      return new GetDatasetItemsResponse(datasetItems);
      
    } catch (IOException e) {
      LOGGER.error("Failed to get dataset items: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to get dataset items - " + e.getMessage(), e);
    }
  }

  private String pollRunStatus(ApifyClient apifyClient, String runResponse, String authToken) throws IOException {
    // Extract run ID from the response (assuming it's JSON with an "id" field)
    // This is a simplified approach - in production you might want to use a proper JSON parser
    String runId = extractRunId(runResponse);
    if (runId == null) {
      throw new IOException("Could not extract run ID from response");
    }
    
    
    while (true) {
      // Wait for finish automatically waits for 1 second or until the run is terminal
        String statusResponse = apifyClient.getRunStatus(runId, authToken, 1);
        
        // Check if run is in terminal state (simplified check)
        if (isRunFinished(statusResponse)) {
          return statusResponse;
        }
    }
  }

  private String extractRunId(String response) {
    try {
      if (response == null || response.trim().isEmpty()) {
        return null;
      }
      
      JsonNode rootNode = objectMapper.readTree(response);
      
      // Look for the run ID in the data object: {"data": {"id": "runId", ...}}
      JsonNode dataNode = rootNode.get("data");
      if (dataNode != null && dataNode.has("id")) {
        return dataNode.get("id").asText();
      }
      
      // Fallback: look for any "id" field at root level
      if (rootNode.has("id")) {
        return rootNode.get("id").asText();
      }
      
      return null;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON response for run ID extraction: {}", e.getMessage());
      return null;
    }
  }

  private boolean isRunFinished(String statusResponse) {
    try {
      if (statusResponse == null || statusResponse.trim().isEmpty()) {
        return false;
      }
      
      JsonNode rootNode = objectMapper.readTree(statusResponse);
      JsonNode dataNode = rootNode.get("data");
      
      if (dataNode != null && dataNode.has("status")) {
        String status = dataNode.get("status").asText();
        return "SUCCEEDED".equals(status) || 
               "FAILED".equals(status) || 
               "ABORTED".equals(status) || 
               "TIMED-OUT".equals(status);
      }
      
      return false;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON response for status check: {}", e.getMessage());
      return false;
    }
  }

  private String extractBuildIdFromTag(String actorResponse, String buildTag) {
    try {
      JsonNode rootNode = objectMapper.readTree(actorResponse);
      // Check if taggedBuilds is nested under "data" property
      JsonNode dataNode = rootNode.path("data");
      JsonNode taggedBuildsNode = dataNode.isMissingNode() 
          ? rootNode.path("taggedBuilds") 
          : dataNode.path("taggedBuilds");
      
      if (taggedBuildsNode.isObject()) {
        JsonNode buildTagNode = taggedBuildsNode.path(buildTag);
        if (buildTagNode.isObject() && buildTagNode.has("buildId")) {
          return buildTagNode.get("buildId").asText();
        }
      }
      
      return null;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON response for build ID extraction: {}", e.getMessage());
      return null;
    }
  }

  private Map<String, Object> extractDefaultInputFromBuild(String buildResponse) {
    Map<String, Object> defaultInput = new HashMap<>();
    
    try {
      JsonNode rootNode = objectMapper.readTree(buildResponse);
      JsonNode actorDefinitionNode = rootNode.path("actorDefinition");
      
      if (actorDefinitionNode.isObject()) {
        JsonNode inputNode = actorDefinitionNode.path("input");
        if (inputNode.isObject()) {
          JsonNode propertiesNode = inputNode.path("properties");
          
          if (propertiesNode.isObject()) {
            // Iterate through each property
            propertiesNode.fields().forEachRemaining(entry -> {
              String propertyName = entry.getKey();
              JsonNode propertyNode = entry.getValue();
              
              if (propertyNode.isObject() && propertyNode.has("prefill")) {
                JsonNode prefillNode = propertyNode.get("prefill");
                Object prefillValue = convertJsonNodeToObject(prefillNode);
                defaultInput.put(propertyName, prefillValue);
              }
            });
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON response for default input extraction: {}", e.getMessage());
    }
    
    return defaultInput;
  }
  
  private Object convertJsonNodeToObject(JsonNode node) {
    if (node.isTextual()) {
      return node.asText();
    } else if (node.isBoolean()) {
      return node.asBoolean();
    } else if (node.isInt()) {
      return node.asInt();
    } else if (node.isLong()) {
      return node.asLong();
    } else if (node.isDouble()) {
      return node.asDouble();
    } else if (node.isArray()) {
      return objectMapper.convertValue(node, Object.class);
    } else if (node.isObject()) {
      Map<String, Object> map = new HashMap<>();
      node.fields().forEachRemaining(entry -> {
        map.put(entry.getKey(), convertJsonNodeToObject(entry.getValue()));
      });
      return map;
    } else if (node.isNull()) {
      return null;
    } else {
      return node.asText();
    }
  }

  private Map<String, Object> convertJsonNodeToMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return new HashMap<>();
    }
    if (!node.isObject()) {
      return new HashMap<>();
    }
    Map<String, Object> map = new HashMap<>();
    node.fields().forEachRemaining(entry -> {
      map.put(entry.getKey(), convertJsonNodeToObject(entry.getValue()));
    });
    return map;
  }

  private String mapToJson(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      LOGGER.warn("Failed to convert map to JSON: {}, map: {}", e.getMessage(), map);
      return "{}";
    }
  }

  private void validateAuthentication(Authentication authentication) {
    if (authentication == null || authentication.token() == null || authentication.token().isEmpty()) {
      throw new ConnectorInputException("Error: Authentication token is required");
    }
  }
}
