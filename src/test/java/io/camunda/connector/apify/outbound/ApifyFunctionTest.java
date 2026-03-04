package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorRequest;
import io.camunda.connector.apify.outbound.dto.ScrapeSingleUrlRequest;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;

import io.camunda.connector.apify.common.ApifyClient;
import io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ApifyFunctionTest {

  ObjectMapper objectMapper = new ObjectMapper();

  private ApifyClient.ResponseResult createResponseResult(int statusCode, String responseBody, byte[] responseBodyBytes, String contentType) {
    return new ApifyClient.ResponseResult(statusCode, responseBody, responseBodyBytes, contentType);
  }

  @Test
  void shouldReturnReceivedMessageWhenExecute() throws Exception {
    var runActorRequest = new RunActorRequest(
      "test-actor",
      null,
      null,
      null,
      null,
      false
    );
    var apifyRequestInput = new ApifyRequestInput(
      runActorRequest,
      null,
      null,
      null,
      null
    );
    var input = new ApifyRequest(
      new Authentication("testToken"),
      new Operation("runActor"),
      apifyRequestInput
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
      .variables(objectMapper.writeValueAsString(input))
      .build();
    assertThatThrownBy(() -> function.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Error");
  }

  @Test
  void shouldReturnResultForUnsupportedOperation() throws Exception {
    var apifyRequestInput = new ApifyRequestInput(
      null,
      null,
      null,
      null,
      null
    );
    var input = new ApifyRequest(
      new Authentication("testToken"),
      new Operation("unsupportedOperation"),
      apifyRequestInput
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();
    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Unsupported operation type");
  }

  @Test
  void testExtractBuildIdFromTagWithRealJson() {
    String actorResponse = """
        {
          "data": {
            "id": "zdc3Pyhyz3m8vjDeM",
            "userId": "wRsJZtadYvn4mBZmm",
            "name": "MyActor",
            "username": "jane35",
            "taggedBuilds": {
              "latest": {
                "buildId": "z2EryhbfhgSyqj6Hn",
                "buildNumber": "0.0.2",
                "finishedAt": "2019-06-10T11:15:49.286Z"
              }
            }
          }
        }
        """;

    String buildId = ActorBuildHelper.extractBuildIdFromTag(actorResponse, "latest");
    assertThat(buildId).isEqualTo("z2EryhbfhgSyqj6Hn");

    buildId = ActorBuildHelper.extractBuildIdFromTag(actorResponse, "nonexistent");
    assertThat(buildId).isNull();
  }

  @Test
  void testExtractDefaultInputFromBuildWithRealJson() throws Exception {
    String buildResponse = """
        {
          "data": {
            "id": "HG7ML7M8z78YcAPEB",
            "actId": "janedoe~my-actor",
            "status": "SUCCEEDED",
            "actorDefinition": {
              "actorSpecification": 1,
              "name": "example-actor",
              "version": "1.0",
              "input": {
                "type": "object",
                "properties": {
                  "prompt": {
                    "type": "string",
                    "description": "The text prompt.",
                    "prefill": "Enter your prompt here"
                  },
                  "maxTokens": {
                    "type": "integer",
                    "description": "Max tokens.",
                    "prefill": 100
                  }
                },
                "required": ["prompt"]
              }
            }
          }
        }
        """;

    var rootNode = objectMapper.readTree(buildResponse);
    var dataNode = rootNode.get("data");
    String buildResponseData = objectMapper.writeValueAsString(dataNode);

    Map<String, Object> defaultInput = ActorBuildHelper.extractDefaultInputFromBuild(buildResponseData);

    assertThat(defaultInput).isNotNull();
    assertThat(defaultInput).hasSize(2);
    assertThat(defaultInput.get("prompt")).isEqualTo("Enter your prompt here");
    assertThat(defaultInput.get("maxTokens")).isEqualTo(100);
  }

  @Test
  void shouldHandleScrapeSingleUrlOperation() throws Exception {
    var scrapeInput = new ScrapeSingleUrlRequest(
      "http://example.com",
      "cheerio"
    );
    var apifyRequestInput = new ApifyRequestInput(
      null,
      null,
      null,
      scrapeInput,
      null
    );
    var input = new ApifyRequest(
      new Authentication("testToken"),
      new Operation("scrapeSingleUrl"),
      apifyRequestInput
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
      .variables(objectMapper.writeValueAsString(input))
      .build();
    assertThatThrownBy(() -> function.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Error");
  }

  // ---- parseKeyValueStoreResponse tests ----

  @Test
  void testParseKeyValueStoreResponse_JsonContent() {
    ApifyFunction function = new ApifyFunction();

    String jsonContent = "{\"name\":\"test\",\"value\":123}";
    byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, jsonContent, jsonBytes, "application/json");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("application/json");
    assertThat(result.jsonValue()).isNotNull();
    assertThat(result.jsonValue().get("name").asText()).isEqualTo("test");
    assertThat(result.jsonValue().get("value").asInt()).isEqualTo(123);
    assertThat(result.textValue()).isNull();
    assertThat(result.base64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_TextContent() {
    ApifyFunction function = new ApifyFunction();

    String textContent = "This is plain text content";
    byte[] textBytes = textContent.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, textContent, textBytes, "text/plain");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("text/plain");
    assertThat(result.textValue()).isEqualTo(textContent);
    assertThat(result.jsonValue()).isNull();
    assertThat(result.base64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_BinaryContent() {
    ApifyFunction function = new ApifyFunction();

    byte[] binaryBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, "", binaryBytes, "image/jpeg");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.base64Value()).isNotNull();
    assertThat(result.base64Value()).isEqualTo(java.util.Base64.getEncoder().encodeToString(binaryBytes));
    assertThat(result.jsonValue()).isNull();
    assertThat(result.textValue()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_EmptyContent() {
    ApifyFunction function = new ApifyFunction();

    byte[] emptyBytes = new byte[0];
    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, "", emptyBytes, "application/octet-stream");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("application/octet-stream");
    assertThat(result.jsonValue()).isNull();
    assertThat(result.textValue()).isNull();
    assertThat(result.base64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_NullContent() {
    ApifyFunction function = new ApifyFunction();

    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, "", null, "application/octet-stream");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("application/octet-stream");
    assertThat(result.jsonValue()).isNull();
    assertThat(result.textValue()).isNull();
    assertThat(result.base64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_ComplexJson() {
    ApifyFunction function = new ApifyFunction();

    String complexJson = """
        {
          "array": [1, 2, 3],
          "nested": {
            "key": "value",
            "number": 42.5
          },
          "boolean": true,
          "null": null
        }
        """;
    byte[] jsonBytes = complexJson.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult =
        createResponseResult(200, complexJson, jsonBytes, "application/json");

    GetKeyValueStoreRecordResponse result = function.parseKeyValueStoreResponse(responseResult);

    assertThat(result).isNotNull();
    assertThat(result.contentType()).isEqualTo("application/json");
    assertThat(result.jsonValue()).isNotNull();
    assertThat(result.jsonValue().has("array")).isTrue();
    assertThat(result.jsonValue().has("nested")).isTrue();
    assertThat(result.textValue()).isNull();
    assertThat(result.base64Value()).isNull();
  }
}
