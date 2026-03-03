package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.core.type.TypeReference;
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
    documentationRef = "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/",
    inputDataClass = ApifyRequest.class)
public class ApifyFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApifyFunction.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  /** Apify's Web Content Scraper Actor (https://apify.com/apify/web-content-scraper) */
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

    LOGGER.debug("Executing operation: {}", operationType);

    return switch (operationType) {
      case "runActor" -> handleRunActor(authentication, apifyRequestInput);
      case "runTask" -> handleRunTask(authentication, apifyRequestInput);
      case "getDatasetItems" -> handleGetDatasetItems(authentication, apifyRequestInput);
      case "scrapeSingleUrl" -> handleScrapeSingleUrl(authentication, apifyRequestInput);
      case "getKeyValueStoreRecord" -> handleGetKeyValueStoreRecord(authentication, apifyRequestInput);
      default -> throw new ConnectorInputException("Unsupported operation type: " + operationType);
    };
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
      String actorResponse = apifyClient.getActor(actorId).responseBody();
      if (actorResponse == null || actorResponse.trim().isEmpty()) {
        throw new RuntimeException("Error: Actor not found - " + actorId);
      }

      String buildResponse = fetchBuildResponse(apifyClient, actorId, actorResponse, input.buildTag());
      Map<String, Object> defaultInput = ActorBuildHelper.extractDefaultInputFromBuild(buildResponse);
      Map<String, Object> userInput = parseInputJson(input.inputJson());

      Map<String, Object> mergedInput = new HashMap<>(defaultInput);
      mergedInput.putAll(userInput);

      var runOptions = new RunOptions(input.timeout(), input.memory(), input.buildTag(), null);
      String response = apifyClient.runActor(actorId, toJson(mergedInput), runOptions).responseBody();

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

  private ApifyResult handleRunTask(Authentication authentication, ApifyRequestInput apifyRequestInput) {
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

      String inputJson = serializeInputJson(input.inputJson());

      var runOptions = new RunOptions(input.timeout(), input.memory(), input.buildTag(), null);
      String response = apifyClient.runTask(taskId, inputJson, runOptions).responseBody();

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
    if (apifyRequestInput == null || apifyRequestInput.getDatasetItemsRequest() == null) {
      throw new ConnectorInputException("Error: getDatasetItemsRequest is null");
    }
    validateAuthentication(authentication);

    GetDatasetItemsRequest datasetInput = apifyRequestInput.getDatasetItemsRequest();

    try (var apifyClient = new ApifyClient(authentication.token())) {
      String datasetItems = apifyClient.getDatasetItems(
          datasetInput.datasetId(), datasetInput.offset(), datasetInput.limit()
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
      Map<String, Object> actorInput = Map.of(
          "startUrls", Collections.singletonList(Map.of("url", input.url())),
          "crawlerType", input.crawlerType(),
          "maxCrawlDepth", 0,
          "maxCrawlPages", 1,
          "maxResults", 1,
          "proxyConfiguration", Map.of("useApifyProxy", true),
          "removeCookieWarnings", true,
          "saveHtml", true,
          "saveMarkdown", true
      );

      String runStartResponse = apifyClient.runActor(
          WEB_CONTENT_SCRAPER_ACTOR_ID, toJson(actorInput), new RunOptions(null, null, null, null)
      ).responseBody();

      String finalRunResponse = RunPollingHelper.pollRunStatus(apifyClient, runStartResponse);
      JsonNode dataNode = OBJECT_MAPPER.readTree(finalRunResponse).path("data");
      JsonNode defaultDatasetIdNode = dataNode.isMissingNode() ? null : dataNode.path("defaultDatasetId");

      if (defaultDatasetIdNode == null || defaultDatasetIdNode.isMissingNode() || !defaultDatasetIdNode.isTextual()) {
        throw new RuntimeException("Error: No dataset ID returned from actor run");
      }

      String datasetItemsJson = apifyClient.getDatasetItems(defaultDatasetIdNode.asText(), 0, 1).responseBody();
      JsonNode itemsNode = OBJECT_MAPPER.readTree(datasetItemsJson);
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
    if (apifyRequestInput == null || apifyRequestInput.getKeyValueStoreRecordRequest() == null) {
      throw new ConnectorInputException("Error: getKeyValueStoreRecordRequest is null");
    }
    validateAuthentication(authentication);

    GetKeyValueStoreRecordRequest recordInput = apifyRequestInput.getKeyValueStoreRecordRequest();

    try (var apifyClient = new ApifyClient(authentication.token())) {
      ApifyClient.ResponseResult result = apifyClient.getKeyValueStoreRecord(
          recordInput.storeId(), recordInput.recordKey()
      );
      return parseKeyValueStoreResponse(result);
    } catch (ApifyClientException e) {
      throw handleApifyClientException("get key-value store record", e);
    } catch (Exception e) {
      LOGGER.error("Failed to get key-value store record: {}", e.getMessage(), e);
      throw new RuntimeException("Error: Failed to get key-value store record - " + e.getMessage(), e);
    }
  }

  // ---- Shared helpers ----

  private String fetchBuildResponse(ApifyClient apifyClient, String actorId, String actorResponse, String buildTag)
      throws IOException {
    String buildResponse;
    if (buildTag != null && !buildTag.trim().isEmpty()) {
      String buildId = ActorBuildHelper.extractBuildIdFromTag(actorResponse, buildTag);
      if (buildId == null) {
        throw new RuntimeException("Error: Build tag '" + buildTag + "' not found for actor " + actorId);
      }
      buildResponse = apifyClient.getBuild(buildId).responseBody();
    } else {
      buildResponse = apifyClient.getDefaultBuild(actorId).responseBody();
    }

    if (buildResponse == null || buildResponse.trim().isEmpty()) {
      throw new RuntimeException("Error: Build not found for actor " + actorId);
    }
    return buildResponse;
  }

  /**
   * Parses a {@link JsonNode} input into a mutable map for merging with defaults.
   * Handles the case where inputJson is a string-encoded JSON.
   */
  private Map<String, Object> parseInputJson(JsonNode inputJson) {
    if (inputJson == null || inputJson.isNull()) {
      return new HashMap<>();
    }

    JsonNode resolved = inputJson;
    if (resolved.isTextual()) {
      try {
        resolved = OBJECT_MAPPER.readTree(resolved.asText());
      } catch (Exception e) {
        LOGGER.warn("Failed to parse inputJson as JSON string: {}", e.getMessage());
        return new HashMap<>();
      }
    }

    if (!resolved.isObject()) {
      return new HashMap<>();
    }
    return OBJECT_MAPPER.convertValue(resolved, MAP_TYPE_REF);
  }

  /**
   * Serializes inputJson for task runs (no merging with defaults).
   * Returns null if inputJson is absent.
   */
  private String serializeInputJson(JsonNode inputJson) throws IOException {
    if (inputJson == null || inputJson.isNull()) {
      return null;
    }
    return inputJson.isTextual()
        ? inputJson.asText()
        : OBJECT_MAPPER.writeValueAsString(inputJson);
  }

  private String toJson(Map<String, Object> map) throws IOException {
    return OBJECT_MAPPER.writeValueAsString(map);
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

  GetKeyValueStoreRecordResponse parseKeyValueStoreResponse(ApifyClient.ResponseResult responseResult) {
    String resolvedContentType = responseResult.contentType() != null
        ? responseResult.contentType() : "application/octet-stream";

    byte[] bodyBytes = responseResult.responseBodyInBytes();
    if (bodyBytes == null || bodyBytes.length == 0) {
      return new GetKeyValueStoreRecordResponse(resolvedContentType, null, null, null);
    }

    String lowerContentType = resolvedContentType.toLowerCase();

    if (lowerContentType.contains("application/json") || lowerContentType.contains("text/json")) {
      try {
        JsonNode jsonNode = OBJECT_MAPPER.readTree(responseResult.responseBody());
        return new GetKeyValueStoreRecordResponse(resolvedContentType, jsonNode, null, null);
      } catch (Exception e) {
        LOGGER.warn("Failed to parse as JSON despite content-type: {}", e.getMessage());
      }
    }

    if (lowerContentType.startsWith("text/")) {
      return new GetKeyValueStoreRecordResponse(resolvedContentType, null, responseResult.responseBody(), null);
    }

    String base64 = Base64.getEncoder().encodeToString(bodyBytes);
    return new GetKeyValueStoreRecordResponse(resolvedContentType, null, null, base64);
  }
}
