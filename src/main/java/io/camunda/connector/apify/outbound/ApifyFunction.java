package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.common.ApifyClientException;
import io.camunda.connector.apify.common.RunOptions;
import io.camunda.connector.apify.common.URLValidator;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsInput;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsResponse;
import io.camunda.connector.apify.outbound.dto.RunActorResponse;
import io.camunda.connector.apify.outbound.dto.RunTaskResponse;
import io.camunda.connector.apify.outbound.dto.ScrapeSingleUrlResponse;
import io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordInput;
import io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Base64;
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
        "apifyRequestInput.getDatasetItemsInput",
        "apifyRequestInput.scrapeSingleUrlInput",
        "apifyRequestInput.getKeyValueStoreRecordInput"
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
  private static final String WEB_CONTENT_SCRAPER_ACTOR_ID = "aYG0l9s7dbB7j3gbS";

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
      case "scrapeSingleUrl":
        return handleScrapeSingleUrl(authentication, apifyRequestInput);
      case "getKeyValueStoreRecord":
        return handleGetKeyValueStoreRecord(authentication, apifyRequestInput);
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

    // Transform actorId to the format "username~actor-name" if it is not already in that format
    final String actorId = input.actorId().replace("/", "~");

    try (var apifyClient = new ApifyClient(authentication.token())) {
      // Get actor details to validate and get build info
      String actorResponse = apifyClient.getActor(actorId).responseBody();
      if (actorResponse == null || actorResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Actor not found - " + actorId);
      }

      // Get build information (either specified build or default)
      String buildResponse = fetchBuildResponse(apifyClient, actorId, actorResponse, input.buildTag());

      // Extract default input values from build definition
      Map<String, Object> defaultInput = ActorBuildHelper.extractDefaultInputFromBuild(buildResponse);
      
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
        input.buildTag(),
        null // Don't wait for finish initially
      );
      String response = apifyClient.runActor(
        actorId,
        mergedInputJson,
        runOptions
      ).responseBody();
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = RunPollingHelper.pollRunStatus(apifyClient, response);
      }
      
      return new RunActorResponse(response);
    } catch (ApifyClientException e) {
      throw handleApifyClientException("run actor", e);
    } catch (Exception e) {
      LOGGER.error("Failed to run actor: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to run actor - " + e.getMessage(), e);
    }
  }

  private String fetchBuildResponse(ApifyClient apifyClient, String actorId, String actorResponse, String buildTag)
      throws IOException {
    String buildResponse;
    if (buildTag != null && !buildTag.trim().isEmpty()) {
      String buildId = ActorBuildHelper.extractBuildIdFromTag(actorResponse, buildTag);
      if (buildId == null) {
        throw new ConnectorInputException(
            "Error: Build tag '" + buildTag + "' not found for actor " + actorId);
      }
      buildResponse = apifyClient.getBuild(buildId).responseBody();
    } else {
      buildResponse = apifyClient.getDefaultBuild(actorId).responseBody();
    }

    if (buildResponse == null || buildResponse.trim().isEmpty()) {
      throw new ConnectorInputException("Error: Build not found for actor " + actorId);
    }
    return buildResponse;
  }

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runTask operation");
    if (apifyRequestInput == null || apifyRequestInput.runTaskInput() == null) {
      throw new ConnectorInputException("Error: runTaskInput is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.runTaskInput();

    // Transform taskId to the format "username~task-name" if it is not already in that format
    final String taskId = input.taskId().replace("/", "~");

    try (var apifyClient = new ApifyClient(authentication.token())) {
      // Check if task exists
      String taskResponse = apifyClient.getTask(taskId).responseBody();
      if (taskResponse == null || taskResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Task not found - " + taskId);
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
        input.buildTag(),
        null // Don't wait for finish initially
      );
      String response = apifyClient.runTask(
        taskId,
        inputJson,
        runOptions
      ).responseBody();
      
      // If waitForFinish is true, poll for completion
      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = RunPollingHelper.pollRunStatus(apifyClient, response);
      }
      
      return new RunTaskResponse(response);
    } catch (ApifyClientException e) {
      throw handleApifyClientException("run task", e);
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
    
    try (var apifyClient = new ApifyClient(authentication.token())) {
      
      String datasetItems = apifyClient.getDatasetItems(
        datasetInput.datasetId(),
        datasetInput.offset(),
        datasetInput.limit()
      ).responseBody();
      
      return new GetDatasetItemsResponse(datasetItems);
      
    } catch (ApifyClientException e) {
      throw handleApifyClientException("get dataset items", e);
    } catch (Exception e) {
      LOGGER.error("Failed to get dataset items: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to get dataset items - " + e.getMessage(), e);
    }
  }

  private ApifyResult handleScrapeSingleUrl(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    if (apifyRequestInput == null || apifyRequestInput.scrapeSingleUrlInput() == null) {
      throw new ConnectorInputException("Error: scrapeSingleUrlInput is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.scrapeSingleUrlInput();
    URLValidator.validateUrl(input.url());

    try (var apifyClient = new ApifyClient(authentication.token())) {
      // Build input for web content scraper actor
      Map<String, Object> actorInput = new HashMap<>();
      Map<String, Object> startUrlObj = new HashMap<>();
      startUrlObj.put("url", input.url());
      actorInput.put("startUrls", Collections.singletonList(startUrlObj));
      actorInput.put("crawlerType", input.crawlerType());
      actorInput.put("maxCrawlDepth", 0);
      actorInput.put("maxCrawlPages", 1);
      actorInput.put("maxResults", 1);
      actorInput.put("proxyConfiguration", Collections.singletonMap("useApifyProxy", true));
      actorInput.put("removeCookieWarnings", true);
      actorInput.put("saveHtml", true);
      actorInput.put("saveMarkdown", true);
      String mergedInputJson = mapToJson(actorInput);

      // Start WCC Actor
      var runOptions = new RunOptions(
        null,
        null,
        null,
        null
      );
      String runStartResponse = apifyClient.runActor(
        WEB_CONTENT_SCRAPER_ACTOR_ID,
        mergedInputJson,
        runOptions
      ).responseBody();

      // Poll for finished status
      String finalRunResponse = RunPollingHelper.pollRunStatus(apifyClient, runStartResponse);
      JsonNode runNode = objectMapper.readTree(finalRunResponse);
      JsonNode dataNode = runNode.path("data");
      JsonNode defaultDatasetIdNode = dataNode.isMissingNode() ? null : dataNode.path("defaultDatasetId");
      if (defaultDatasetIdNode == null || defaultDatasetIdNode.isMissingNode() || !defaultDatasetIdNode.isTextual()) {
        throw new RuntimeException("Error: No dataset ID returned from actor run");
      }
      String datasetId = defaultDatasetIdNode.asText();
      
      // Fetch first item from dataset
      String datasetItemsJson = apifyClient.getDatasetItems(datasetId, 0, 1).responseBody();
      JsonNode itemsNode = objectMapper.readTree(datasetItemsJson);
      if (!itemsNode.isArray() || itemsNode.isEmpty()) {
        throw new RuntimeException("Error: No items found in dataset for URL: " + input.url());
      }
      
      // Remove text field to reduce usage of tokens if AI Agent is used in the process
      ObjectNode itemNode = (ObjectNode) itemsNode.get(0);
      itemNode.remove("text");
      
      return new ScrapeSingleUrlResponse(itemNode.toString());
    } catch (ApifyClientException e) {
      throw handleApifyClientException("scrape single URL", e);
    } catch (Exception e) {
      LOGGER.error("Failed to scrape single URL: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to scrape single URL - " + e.getMessage(), e);
    }
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
      node.properties().forEach(entry -> {
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
    node.properties().forEach(entry -> {
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

  /**
   * Translates an {@link ApifyClientException} into the appropriate Camunda exception type.
   * User-correctable errors (400-404) become {@link ConnectorInputException};
   * all others become {@link RuntimeException}.
   */
  private RuntimeException handleApifyClientException(String operation, ApifyClientException e) {
    LOGGER.error("Failed to {}: {}", operation, e.getMessage(), e);
    if (e.isLikelyUserError()) {
      return new ConnectorInputException("Error: Failed to " + operation + " - " + e.getMessage(), e);
    }
    return new RuntimeException("Error: Failed to " + operation + " - " + e.getMessage(), e);
  }

  private GetKeyValueStoreRecordResponse handleGetKeyValueStoreRecord(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    GetKeyValueStoreRecordInput recordInput = apifyRequestInput.getKeyValueStoreRecordInput();
    
    if (recordInput == null) {
      throw new ConnectorInputException("Error: getKeyValueStoreRecordInput is null");
    }
    
    validateAuthentication(authentication);
    
    try (var apifyClient = new ApifyClient(authentication.token())) {
      
      ApifyClient.ResponseResult result = apifyClient.getKeyValueStoreRecord(
        recordInput.storeId(),
        recordInput.recordKey()
      );
      
      return parseKeyValueStoreResponse(result);
      
    } catch (ApifyClientException e) {
      throw handleApifyClientException("get key-value store record", e);
    } catch (Exception e) {
      LOGGER.error("Failed to get key-value store record: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to get key-value store record - " + e.getMessage(), e);
    }
  }

  private GetKeyValueStoreRecordResponse parseKeyValueStoreResponse(ApifyClient.ResponseResult responseResult) {
    GetKeyValueStoreRecordResponse result = new GetKeyValueStoreRecordResponse();
    
    // Use the content type from the HTTP response header
    String contentTypeFromHeader = responseResult.contentType();
    result.setContentType(contentTypeFromHeader != null ? contentTypeFromHeader : "application/octet-stream");
    
    byte[] bodyBytes = responseResult.responseBodyInBytes();
    if (bodyBytes == null || bodyBytes.length == 0) {
      return result;
    }
    
    // Decision logic: Determine if content is JSON, text, or binary (e.g., image)
    // Strategy: Check for binary magic bytes first, then try JSON, then check if valid UTF-8 text
    
    // Use the content type from HTTP header to determine how to parse the response
    String contentType = contentTypeFromHeader != null ? contentTypeFromHeader.toLowerCase() : "application/octet-stream";
    
    // If content type indicates JSON, try to parse as JSON
    if (contentType.contains("application/json") || contentType.contains("text/json")) {
      try {
        String jsonString = responseResult.responseBody();
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        result.setJsonValue(jsonNode);
        return result;
      } catch (Exception e) {
        // If JSON parsing fails, fall through to text/binary handling
        LOGGER.warn("Failed to parse as JSON despite content-type: {}", e.getMessage());
      }
    }
    
    // If content type indicates text, treat as text
    if (contentType.startsWith("text/")) {
      try {
        String textValue = responseResult.responseBody();
        result.setTextValue(textValue);
        return result;
      } catch (Exception e) {
        // If text conversion fails, fall through to binary
        LOGGER.warn("Failed to convert to text: {}", e.getMessage());
      }
    }
    
    // For binary content types or if parsing failed, return as binary
    result.setBase64Value(Base64.getEncoder().encodeToString(bodyBytes));
    return result;
  }
}
