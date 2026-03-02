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
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsRequest;
import io.camunda.connector.apify.outbound.dto.GetDatasetItemsResponse;
import io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordRequest;
import io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse;
import io.camunda.connector.apify.outbound.dto.RunActorResponse;
import io.camunda.connector.apify.outbound.dto.RunTaskResponse;
import io.camunda.connector.apify.outbound.dto.ScrapeSingleUrlResponse;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OutboundConnector(
    name = "APIFY",
    inputVariables = {
        "authentication",
        "operation",
        "apifyRequestInput",
        "apifyRequestInput.runActorRequest",
        "apifyRequestInput.runTaskRequest",
        "apifyRequestInput.getDatasetItemsRequest",
        "apifyRequestInput.scrapeSingleUrlRequest",
        "apifyRequestInput.getKeyValueStoreRecordRequest"
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
  private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FAILED", "ABORTED", "TIMED-OUT");
  private static final int MAX_POLL_DURATION_SECONDS = 3600;
  private static final String WEB_CONTENT_SCRAPER_ACTOR_ID = "aYG0l9s7dbB7j3gbS";

  // ---- Entry point ----

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

  // ---- Operation handlers ----

  private ApifyResult handleRunActor(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    if (apifyRequestInput == null || apifyRequestInput.runActorRequest() == null) {
      throw new ConnectorInputException("Error: runActorRequest is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.runActorRequest();

    final String actorId = input.actorId().replace("/", "~");

    try (var apifyClient = new ApifyClient(authentication.token())) {
      ApifyClient.ResponseResult actorResponseResult = apifyClient.getActor(actorId);
      String actorResponse = actorResponseResult.responseBody();
      if (actorResponse == null || actorResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Actor not found - " + actorId);
      }

      String buildResponse;
      if (input.buildTag() != null && !input.buildTag().trim().isEmpty()) {
        String buildId = extractBuildIdFromTag(actorResponse, input.buildTag());
        if (buildId == null) {
          throw new RuntimeException("Error: Build tag '" + input.buildTag() + "' not found for actor " + actorId);
        }
        buildResponse = apifyClient.getBuild(buildId).responseBody();
      } else {
        buildResponse = apifyClient.getDefaultBuild(actorId).responseBody();
      }

      if (buildResponse == null || buildResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Build not found for actor " + actorId);
      }

      Map<String, Object> defaultInput = extractDefaultInputFromBuild(buildResponse);

      Map<String, Object> userInput = new HashMap<>();
      if (input.inputJson() != null && !input.inputJson().isNull()) {
        JsonNode inputJsonNode = input.inputJson();
        if (inputJsonNode.isTextual()) {
          try {
            inputJsonNode = objectMapper.readTree(inputJsonNode.asText());
          } catch (Exception e) {
            LOGGER.warn("Failed to parse inputJson as JSON string: {}", e.getMessage());
          }
        }
        userInput = convertJsonNodeToMap(inputJsonNode);
      }

      Map<String, Object> mergedInput = new HashMap<>(defaultInput);
      mergedInput.putAll(userInput);

      String mergedInputJson = mapToJson(mergedInput);

      var runOptions = new RunOptions(
        input.timeout(),
        input.memory(),
        input.buildTag(),
        null
      );
      ApifyClient.ResponseResult runResponseResult = apifyClient.runActor(
        actorId,
        mergedInputJson,
        runOptions
      );
      String response = runResponseResult.responseBody();

      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response);
      }

      return new RunActorResponse(response);
    } catch (ApifyClientException e) {
      throw handleApifyClientException("run actor", e);
    } catch (Exception e) {
      LOGGER.error("Failed to run actor: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to run actor - " + e.getMessage(), e);
    }
  }

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    LOGGER.info("Handling runTask operation");
    if (apifyRequestInput == null || apifyRequestInput.runTaskRequest() == null) {
      throw new ConnectorInputException("Error: runTaskRequest is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.runTaskRequest();

    final String taskId = input.taskId().replace("/", "~");

    try (var apifyClient = new ApifyClient(authentication.token())) {
      String taskResponse = apifyClient.getTask(taskId).responseBody();
      if (taskResponse == null || taskResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Task not found - " + taskId);
      }

      String inputJson = null;
      if (input.inputJson() != null && !input.inputJson().isNull()) {
        JsonNode inputJsonNode = input.inputJson();
        if (inputJsonNode.isTextual()) {
          inputJson = inputJsonNode.asText();
        } else {
          inputJson = objectMapper.writeValueAsString(inputJsonNode);
        }
      }

      var runOptions = new RunOptions(
        input.timeout(),
        input.memory(),
        input.buildTag(),
        null
      );
      ApifyClient.ResponseResult runResponseResult = apifyClient.runTask(
        taskId,
        inputJson,
        runOptions
      );
      String response = runResponseResult.responseBody();

      if (Boolean.TRUE.equals(input.waitForFinish())) {
        response = pollRunStatus(apifyClient, response);
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
    GetDatasetItemsRequest datasetInput = apifyRequestInput.getDatasetItemsRequest();

    if (datasetInput == null) {
      throw new ConnectorInputException("Error: getDatasetItemsRequest is null");
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
    if (apifyRequestInput == null || apifyRequestInput.scrapeSingleUrlRequest() == null) {
      throw new ConnectorInputException("Error: scrapeSingleUrlRequest is null");
    }
    validateAuthentication(authentication);

    var input = apifyRequestInput.scrapeSingleUrlRequest();
    URLValidator.validateUrl(input.url());

    try (var apifyClient = new ApifyClient(authentication.token())) {
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

      String finalRunResponse = pollRunStatus(apifyClient, runStartResponse);
      JsonNode runNode = objectMapper.readTree(finalRunResponse);
      JsonNode dataNode = runNode.path("data");
      JsonNode defaultDatasetIdNode = dataNode.isMissingNode() ? null : dataNode.path("defaultDatasetId");
      if (defaultDatasetIdNode == null || defaultDatasetIdNode.isMissingNode() || !defaultDatasetIdNode.isTextual()) {
        throw new RuntimeException("Error: No dataset ID returned from actor run");
      }
      String datasetId = defaultDatasetIdNode.asText();

      String datasetItemsJson = apifyClient.getDatasetItems(datasetId, 0, 1).responseBody();
      JsonNode itemsNode = objectMapper.readTree(datasetItemsJson);
      if (!itemsNode.isArray() || itemsNode.isEmpty()) {
        throw new RuntimeException("Error: No items found in dataset for URL: " + input.url());
      }

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

  private GetKeyValueStoreRecordResponse handleGetKeyValueStoreRecord(Authentication authentication, ApifyRequestInput apifyRequestInput) {
    GetKeyValueStoreRecordRequest recordInput = apifyRequestInput.getKeyValueStoreRecordRequest();

    if (recordInput == null) {
      throw new ConnectorInputException("Error: getKeyValueStoreRecordRequest is null");
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
    } catch (IOException e) {
      LOGGER.error("Failed to get key-value store record: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get key-value store record: " + e.getMessage(), e);
    }
  }

  // ---- Run polling helpers ----

  private String pollRunStatus(ApifyClient apifyClient, String runResponse) throws IOException {
    String runId = extractRunId(runResponse);
    if (runId == null) {
      throw new IOException("Could not extract run ID from response");
    }

    // Safety net: stop polling after MAX_POLL_DURATION_SECONDS to prevent infinite loops
    // if a run gets stuck in a non-terminal state.
    long deadline = System.currentTimeMillis() + MAX_POLL_DURATION_SECONDS * 1000L;

    while (System.currentTimeMillis() < deadline) {
      String statusResponse = apifyClient.getRunStatus(runId, 1).responseBody();

      if (isRunFinished(statusResponse)) {
        return statusResponse;
      }
    }

    throw new IOException(
        "Polling timed out after " + MAX_POLL_DURATION_SECONDS + " seconds for run " + runId);
  }

  private String extractRunId(String response) {
    try {
      if (response == null || response.trim().isEmpty()) {
        return null;
      }

      JsonNode rootNode = objectMapper.readTree(response);

      JsonNode dataNode = rootNode.get("data");
      if (dataNode != null && dataNode.has("id")) {
        return dataNode.get("id").asText();
      }

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
        return TERMINAL_STATUSES.contains(status);
      }

      return false;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse JSON response for status check: {}", e.getMessage());
      return false;
    }
  }

  // ---- Actor/build helpers ----

  private String extractBuildIdFromTag(String actorResponse, String buildTag) {
    try {
      JsonNode rootNode = objectMapper.readTree(actorResponse);
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
            propertiesNode.properties().forEach(entry -> {
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

  // ---- JSON / validation utilities ----

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

  private void validateAuthentication(Authentication authentication) {
    if (authentication == null || authentication.token() == null || authentication.token().isEmpty()) {
      throw new ConnectorInputException("Error: Authentication token is required");
    }
  }

  private GetKeyValueStoreRecordResponse parseKeyValueStoreResponse(ApifyClient.ResponseResult responseResult) {
    GetKeyValueStoreRecordResponse result = new GetKeyValueStoreRecordResponse();

    String contentTypeFromHeader = responseResult.contentType();
    result.setContentType(contentTypeFromHeader != null ? contentTypeFromHeader : "application/octet-stream");

    byte[] bodyBytes = responseResult.responseBodyInBytes();
    if (bodyBytes == null || bodyBytes.length == 0) {
      return result;
    }

    String contentType = contentTypeFromHeader != null ? contentTypeFromHeader.toLowerCase() : "application/octet-stream";

    if (contentType.contains("application/json") || contentType.contains("text/json")) {
      try {
        String jsonString = responseResult.responseBody();
        JsonNode jsonNode = objectMapper.readTree(jsonString);
        result.setJsonValue(jsonNode);
        return result;
      } catch (Exception e) {
        LOGGER.warn("Failed to parse as JSON despite content-type: {}", e.getMessage());
      }
    }

    if (contentType.startsWith("text/")) {
      try {
        String textValue = responseResult.responseBody();
        result.setTextValue(textValue);
        return result;
      } catch (Exception e) {
        LOGGER.warn("Failed to convert to text: {}", e.getMessage());
      }
    }

    result.setBase64Value(Base64.getEncoder().encodeToString(bodyBytes));
    return result;
  }
}
