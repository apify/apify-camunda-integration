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
      
      // Parse user input JSON
      Map<String, Object> userInput = parseJsonToMap(input.inputJson());
      
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

  private String extractRunId(String response) {
    // Simple extraction - in production use proper JSON parsing
    int idIndex = response.indexOf("\"id\":\"");
    if (idIndex != -1) {
      int start = idIndex + 6;
      int end = response.indexOf("\"", start);
      if (end != -1) {
        return response.substring(start, end);
      }
    }
    return null;
  }

  private boolean isRunFinished(String statusResponse) {
    // Simple check for terminal states - in production use proper JSON parsing
    return statusResponse.contains("\"status\":\"SUCCEEDED\"") ||
           statusResponse.contains("\"status\":\"FAILED\"") ||
           statusResponse.contains("\"status\":\"ABORTED\"") ||
           statusResponse.contains("\"status\":\"TIMED-OUT\"");
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
    
    // Simple JSON parsing for basic key-value pairs
    // This is a simplified approach - in production you'd want to use a proper JSON library
    json = json.trim();
    if (!json.startsWith("{") || !json.endsWith("}")) {
      return result;
    }
    
    // Remove outer braces
    json = json.substring(1, json.length() - 1);
    
    // Split by comma, but be careful about nested objects/arrays
    String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    
    for (String pair : pairs) {
      String[] keyValue = pair.split(":", 2);
      if (keyValue.length == 2) {
        String key = keyValue[0].trim();
        String value = keyValue[1].trim();
        
        // Remove quotes from key
        if (key.startsWith("\"") && key.endsWith("\"")) {
          key = key.substring(1, key.length() - 1);
        }
        
        // Parse value
        if (value.startsWith("\"") && value.endsWith("\"")) {
          result.put(key, value.substring(1, value.length() - 1));
        } else if ("true".equals(value) || "false".equals(value)) {
          result.put(key, Boolean.parseBoolean(value));
        } else if (value.matches("-?\\d+")) {
          result.put(key, Integer.parseInt(value));
        } else if (value.matches("-?\\d+\\.\\d+")) {
          result.put(key, Double.parseDouble(value));
        } else {
          result.put(key, value);
        }
      }
    }
    
    return result;
  }

  private String mapToJson(Map<String, Object> map) {
    if (map.isEmpty()) {
      return "{}";
    }
    
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (!first) {
        json.append(",");
      }
      first = false;
      
      json.append("\"").append(entry.getKey()).append("\":");
      
      Object value = entry.getValue();
      if (value instanceof String) {
        json.append("\"").append(value).append("\"");
      } else if (value instanceof Boolean || value instanceof Number) {
        json.append(value);
      } else {
        json.append("\"").append(value.toString()).append("\"");
      }
    }
    
    json.append("}");
    return json.toString();
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
