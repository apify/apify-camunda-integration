package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    if (apifyRequestInput == null || apifyRequestInput.runActorInput() == null) {
      return new ApifyResult("Error: runActorInput is null");
    }
    if (authentication == null || authentication.token() == null || authentication.token().isEmpty()) {
      return new ApifyResult("Error: Authentication token is required");
    }

    var input = apifyRequestInput.runActorInput();

    try (ApifyClient apifyClient = new ApifyClient()) {
      // Get actor details to validate and get build info
      String actorResponse = apifyClient.getActor(input.actorId(), authentication.token());
      if (actorResponse == null || actorResponse.trim().isEmpty()) {
        return new ApifyResult("Error: Actor not found - " + input.actorId());
      }

      // Get build information (either specified build or default)
      String buildResponse;
      if (input.build() != null && !input.build().trim().isEmpty()) {
        // Get build by tag from actor's taggedBuilds
        String buildId = extractBuildIdFromTag(actorResponse, input.build());
        if (buildId == null) {
          return new ApifyResult("Error: Build tag '" + input.build() + "' not found for actor " + input.actorId());
        }
        buildResponse = apifyClient.getBuild(buildId, authentication.token());
      } else {
        // Get default build
        buildResponse = apifyClient.getDefaultBuild(input.actorId(), authentication.token());
      }

      if (buildResponse == null || buildResponse.trim().isEmpty()) {
        return new ApifyResult("Error: Build not found for actor " + input.actorId());
      }

      // Extract default input values from build definition
      Map<String, Object> defaultInput = extractDefaultInputFromBuild(buildResponse);
      
      // Parse user input JSON (escape it first to handle literal \n, \t, etc.)
      String escapedInputJson = escapeJsonString(input.inputJson());
      Map<String, Object> userInput = parseJsonToMap(escapedInputJson);
      
      // Merge default input with user input (user input takes precedence)
      Map<String, Object> mergedInput = new HashMap<>(defaultInput);
        mergedInput.putAll(userInput);
      
      // Convert merged input back to JSON
      String mergedInputJson = mapToJson(mergedInput);
      
      // Run the actor with merged input and parameters
      String response = apifyClient.runActor(
        input.actorId(),
        authentication.token(),
        mergedInputJson,
        input.timeout(),
        input.memory(),
        input.build(),
        null // Don't wait for finish initially
      );
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response, authentication.token());
      }
      
      return new ApifyResult(response);
    } catch (IOException e) {
      LOGGER.error("Failed to run actor: {}", e.getMessage(), e);
      return new ApifyResult("Error: Failed to run actor - " + e.getMessage());
    }
  }

  private String pollRunStatus(ApifyClient apifyClient, String runResponse, String authToken) throws IOException {
    // Extract run ID from the response (assuming it's JSON with an "id" field)
    // This is a simplified approach - in production you might want to use a proper JSON parser
    String runId = extractRunId(runResponse);
    if (runId == null) {
      throw new IOException("Could not extract run ID from response");
    }
    
    // Poll for completion (simplified - in production you'd want more sophisticated polling)
    int maxAttempts = 60; // 5 minutes with 5-second intervals
    int attempt = 0;
    
    while (attempt < maxAttempts) {
      try {
        Thread.sleep(5000); // Wait 5 seconds
        String statusResponse = apifyClient.getRunStatus(runId, authToken);
        
        // Check if run is in terminal state (simplified check)
        if (isRunFinished(statusResponse)) {
          return statusResponse;
        }
        
        attempt++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Polling interrupted", e);
      }
    }
    
    throw new IOException("Run did not complete within timeout period");
  }

  /**
   * Properly escapes JSON string to handle literal \n, \t, \r, etc. characters
   * that users might input in the JSON field.
   */
  private String escapeJsonString(String json) {
    if (json == null || json.trim().isEmpty()) {
      return json;
    }
    
    // Replace literal newlines with escaped newlines
    json = json.replace("\n", "\\n");
    json = json.replace("\r", "\\r");
    json = json.replace("\t", "\\t");
    json = json.replace("\b", "\\b");
    json = json.replace("\f", "\\f");
    json = json.replace("\\", "\\\\");
    
    return json;
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
    // Look for taggedBuilds section and extract buildId for the given tag
    Pattern pattern = Pattern.compile("\"taggedBuilds\"\\s*:\\s*\\{[^}]*\"" + Pattern.quote(buildTag) + "\"\\s*:\\s*\\{[^}]*\"buildId\"\\s*:\\s*\"([^\"]+)\"");
    Matcher matcher = pattern.matcher(actorResponse);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private Map<String, Object> extractDefaultInputFromBuild(String buildResponse) {
    Map<String, Object> defaultInput = new HashMap<>();
    
    // Look for actorDefinition.input.properties and extract prefill values
    Pattern propertiesPattern = Pattern.compile("\"actorDefinition\"\\s*:\\s*\\{[^}]*\"input\"\\s*:\\s*\\{[^}]*\"properties\"\\s*:\\s*\\{([^}]+(?:\\{[^}]*\\}[^}]*)*)\\}");
    Matcher propertiesMatcher = propertiesPattern.matcher(buildResponse);
    
    if (propertiesMatcher.find()) {
      String propertiesSection = propertiesMatcher.group(1);
      
      // Extract each property with its prefill value
      Pattern propertyPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{[^}]*\"prefill\"\\s*:\\s*([^,}]+)");
      Matcher propertyMatcher = propertyPattern.matcher(propertiesSection);
      
      while (propertyMatcher.find()) {
        String key = propertyMatcher.group(1);
        String value = propertyMatcher.group(2).trim();
        
        // Remove quotes and handle different value types
        if (value.startsWith("\"") && value.endsWith("\"")) {
          defaultInput.put(key, value.substring(1, value.length() - 1));
        } else if ("true".equals(value) || "false".equals(value)) {
          defaultInput.put(key, Boolean.parseBoolean(value));
        } else if (value.matches("-?\\d+")) {
          defaultInput.put(key, Integer.parseInt(value));
        } else if (value.matches("-?\\d+\\.\\d+")) {
          defaultInput.put(key, Double.parseDouble(value));
        } else {
          defaultInput.put(key, value);
        }
      }
    }
    
    return defaultInput;
  }

  private Map<String, Object> parseJsonToMap(String json) {
    Map<String, Object> result = new HashMap<>();
    
    if (json == null || json.trim().isEmpty()) {
      return result;
    }
    
    try {
      JsonNode rootNode = objectMapper.readTree(json);
      
      if (rootNode.isObject()) {
        rootNode.fields().forEachRemaining(entry -> {
          String key = entry.getKey();
          JsonNode value = entry.getValue();
          result.put(key, convertJsonNodeToObject(value));
        });
      }
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON input: {}", e.getMessage());
    }
    
    return result;
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

  private String mapToJson(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (Exception e) {
      LOGGER.warn("Failed to convert map to JSON: {}", e.getMessage());
      return "{}";
    }
  }

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runTask operation");
    if (apifyRequestInput == null || apifyRequestInput.runTaskInput() == null) {
      return new ApifyResult("Error: runTaskInput is null");
    }
    if (authentication == null || authentication.token() == null || authentication.token().isEmpty()) {
      return new ApifyResult("Error: Authentication token is required");
    }

    var input = apifyRequestInput.runTaskInput();

    try (ApifyClient apifyClient = new ApifyClient()) {
      // Use input JSON if provided, otherwise use task's default input (escape it first)
      String inputJson = escapeJsonString(input.inputJson());
      
      // Run the task with parameters
      String response = apifyClient.runTask(
        input.taskId(),
        authentication.token(),
        inputJson,
        input.timeout(),
        input.memory(),
        input.build(),
        null // Don't wait for finish initially
      );
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response, authentication.token());
      }
      
      return new ApifyResult(response);
    } catch (IOException e) {
      LOGGER.error("Failed to run task: {}", e.getMessage(), e);
      return new ApifyResult("Error: Failed to run task - " + e.getMessage());
    }
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
