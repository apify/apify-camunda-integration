package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorInput;
import io.camunda.connector.apify.outbound.dto.ScrapeSingleUrlInput;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;

import io.camunda.connector.apify.common.ApifyClient;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class MyFunctionTest {

  ObjectMapper objectMapper = new ObjectMapper();

  private ApifyClient.ResponseResult createResponseResult(int statusCode, String responseBody, byte[] responseBodyBytes, String contentType) {
    return new ApifyClient.ResponseResult(statusCode, responseBody, responseBodyBytes, contentType);
  }

  @Test
  void shouldReturnReceivedMessageWhenExecute() throws Exception {
    var runActorInput = new RunActorInput(
      "test-actor",
      null,
      null,
      null,
      null,
      false
    );
    var apifyRequestInput = new ApifyRequestInput(
      runActorInput,
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
  void testExtractBuildIdFromTagWithRealJson() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "extractBuildIdFromTag", String.class, String.class);
    method.setAccessible(true);

    String actorResponse = """
        {
          "data": {
            "id": "zdc3Pyhyz3m8vjDeM",
            "userId": "wRsJZtadYvn4mBZmm",
            "name": "MyActor",
            "username": "jane35",
            "description": "My favourite Actor!",
            "isPublic": false,
            "createdAt": "2019-07-08T11:27:57.401Z",
            "modifiedAt": "2019-07-08T14:01:05.546Z",
            "stats": {
              "totalBuilds": 9,
              "totalRuns": 16,
              "totalUsers": 6,
              "totalUsers7Days": 2,
              "totalUsers30Days": 6,
              "totalUsers90Days": 6,
              "totalMetamorphs": 2,
              "lastRunStartedAt": "2019-07-08T14:01:05.546Z"
            },
            "versions": [
              {
                "versionNumber": "0.1",
                "envVars": null,
                "sourceType": "SOURCE_FILES",
                "applyEnvVarsToBuild": false,
                "buildTag": "latest",
                "sourceFiles": []
              },
              {
                "versionNumber": "0.2",
                "sourceType": "GIT_REPO",
                "envVars": null,
                "applyEnvVarsToBuild": false,
                "buildTag": "latest",
                "gitRepoUrl": "https://github.com/jane35/my-actor"
              },
              {
                "versionNumber": "0.3",
                "sourceType": "TARBALL",
                "envVars": null,
                "applyEnvVarsToBuild": false,
                "buildTag": "latest",
                "tarballUrl": "https://github.com/jane35/my-actor/archive/master.zip"
              },
              {
                "versionNumber": "0.4",
                "sourceType": "GITHUB_GIST",
                "envVars": null,
                "applyEnvVarsToBuild": false,
                "buildTag": "latest",
                "gitHubGistUrl": "https://gist.github.com/jane35/e51feb784yu89"
              }
            ],
            "defaultRunOptions": {
              "build": "latest",
              "timeoutSecs": 3600,
              "memoryMbytes": 2048,
              "restartOnError": false
            },
            "exampleRunInput": {
              "body": "{ \\"helloWorld\\": 123 }",
              "contentType": "application/json; charset=utf-8"
            },
            "isDeprecated": false,
            "deploymentKey": "ssh-rsa AAAA ...",
            "title": "My Actor",
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

    // Test extracting build ID for "latest" tag (with data wrapper)
    String buildId = (String) method.invoke(function, actorResponse, "latest");
    assertThat(buildId).isEqualTo("z2EryhbfhgSyqj6Hn");

    // Test with non-existent tag
    buildId = (String) method.invoke(function, actorResponse, "nonexistent");
    assertThat(buildId).isNull();
  }

  @Test
  void testExtractDefaultInputFromBuildWithRealJson() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "extractDefaultInputFromBuild", String.class);
    method.setAccessible(true);

    String buildResponse = """
        {
          "data": {
            "id": "HG7ML7M8z78YcAPEB",
            "actId": "janedoe~my-actor",
            "userId": "klmdEpoiojmdEMlk3",
            "startedAt": "2019-11-30T07:34:24.202Z",
            "finishedAt": "2019-12-12T09:30:12.202Z",
            "status": "SUCCEEDED",
            "meta": {
              "origin": "WEB",
              "clientIp": "172.234.12.34",
              "userAgent": "Mozilla/5.0 (iPad)"
            },
            "stats": {
              "durationMillis": 1000,
              "runTimeSecs": 45.718,
              "computeUnits": 0.012699444444444444
            },
            "options": {
              "useCache": false,
              "betaPackages": false,
              "memoryMbytes": 1024,
              "diskMbytes": 2048
            },
            "usage": {
              "ACTOR_COMPUTE_UNITS": 0.08
            },
            "usageTotalUsd": 0.02,
            "usageUsd": {
              "ACTOR_COMPUTE_UNITS": 0.02
            },
            "inputSchema": "{\\n  \\\"title\\\": \\\"Schema for ... }",
            "readme": "# Magic Actor\\nThis Actor is magic.",
            "buildNumber": "0.1.1",
            "actorDefinition": {
              "actorSpecification": 1,
              "name": "exmpla-actor",
              "version": "1.0",
              "buildTag": "latest",
              "environmentVariables": {
                "DEBUG_MODE": "false"
              },
              "input": {
                "type": "object",
                "properties": {
                  "prompt": {
                    "type": "string",
                    "description": "The text prompt to generate completions for.",
                    "prefill": "Enter your prompt here"
                  },
                  "maxTokens": {
                    "type": "integer",
                    "description": "The maximum number of tokens to generate.",
                    "prefill": 100
                  }
                },
                "required": [
                  "prompt"
                ]
              },
              "storages": {
                "dataset": {
                  "type": "object",
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "Unique identifier for the generated text."
                    },
                    "text": {
                      "type": "string",
                      "description": "The generated text output from the model."
                    }
                  },
                  "required": [
                    "id",
                    "text"
                  ]
                }
              },
              "minMemoryMbytes": 512,
              "maxMemoryMbytes": 2048,
              "usesStandbyMode": false
            }
          }
        }
        """;

    // Extract the data object since the method expects actorDefinition at root level
    var rootNode = objectMapper.readTree(buildResponse);
    var dataNode = rootNode.get("data");
    String buildResponseData = objectMapper.writeValueAsString(dataNode);

    @SuppressWarnings("unchecked")
    Map<String, Object> defaultInput = (Map<String, Object>) method.invoke(function, buildResponseData);

    assertThat(defaultInput).isNotNull();
    assertThat(defaultInput).hasSize(2); // prompt and maxTokens have prefill values
    assertThat(defaultInput.get("prompt")).isEqualTo("Enter your prompt here");
    assertThat(defaultInput.get("maxTokens")).isEqualTo(100);
  }
  
  void shouldHandleScrapeSingleUrlOperation() throws Exception {
    var scrapeInput = new ScrapeSingleUrlInput(
      "http://example.com",
      null // crawlerType, defaults in handler
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
    // Note: This test will likely throw a RuntimeException due to API error (invalid token/actor)
    assertThatThrownBy(() -> function.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Error");
  }

  @Test
  void testParseKeyValueStoreResponse_JsonContent() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", io.camunda.connector.apify.common.ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    String jsonContent = "{\"name\":\"test\",\"value\":123}";
    byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, jsonContent, jsonBytes, "application/json");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("application/json");
    assertThat(result.getJsonValue()).isNotNull();
    assertThat(result.getJsonValue().get("name").asText()).isEqualTo("test");
    assertThat(result.getJsonValue().get("value").asInt()).isEqualTo(123);
    assertThat(result.getTextValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_TextContent() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    String textContent = "This is plain text content";
    byte[] textBytes = textContent.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, textContent, textBytes, "text/plain");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("text/plain");
    assertThat(result.getTextValue()).isEqualTo(textContent);
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_BinaryContent() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    // Simulate binary content (e.g., image bytes)
    byte[] binaryBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 
                                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46}; // JPEG header
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, "", binaryBytes, "image/jpeg");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("image/jpeg");
    assertThat(result.getBase64Value()).isNotNull();
    assertThat(result.getBase64Value()).isEqualTo(java.util.Base64.getEncoder().encodeToString(binaryBytes));
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getTextValue()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_EmptyContent() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    byte[] emptyBytes = new byte[0];
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, "", emptyBytes, "application/octet-stream");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("application/octet-stream");
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getTextValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_NullContent() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, "", null, "application/octet-stream");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("application/octet-stream");
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getTextValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_TextWithSpecialCharacters() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    String textContent = "Hello World! こんにちは 🌍";
    byte[] textBytes = textContent.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, textContent, textBytes, "text/plain");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("text/plain");
    assertThat(result.getTextValue()).isEqualTo(textContent);
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_InvalidJsonButValidText() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    // Invalid JSON but valid text
    String invalidJson = "{name: test, value: 123}"; // Missing quotes
    byte[] textBytes = invalidJson.getBytes(StandardCharsets.UTF_8);
    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, invalidJson, textBytes, "text/plain");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("text/plain");
    assertThat(result.getTextValue()).isEqualTo(invalidJson);
    assertThat(result.getJsonValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_TextWithLowPrintableRatio() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

    // Create text with less than 80% printable characters
    // Mix of printable and non-printable control characters
    byte[] mixedBytes = new byte[100];
    for (int i = 0; i < 50; i++) {
      mixedBytes[i] = (byte) ('A' + (i % 26)); // Printable
    }
    for (int i = 50; i < 100; i++) {
      mixedBytes[i] = (byte) (i % 32); // Non-printable control chars
    }

    ApifyClient.ResponseResult responseResult = 
        createResponseResult(200, "", mixedBytes, "application/octet-stream");

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    // Should be treated as binary since printable ratio is < 80%
    assertThat(result.getContentType()).isEqualTo("application/octet-stream");
    assertThat(result.getBase64Value()).isNotNull();
    assertThat(result.getTextValue()).isNull();
    assertThat(result.getJsonValue()).isNull();
  }

  @Test
  void testParseKeyValueStoreResponse_ComplexJson() throws Exception {
    ApifyFunction function = new ApifyFunction();
    Method method = ApifyFunction.class.getDeclaredMethod(
        "parseKeyValueStoreResponse", ApifyClient.ResponseResult.class);
    method.setAccessible(true);

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

    var result = (io.camunda.connector.apify.outbound.dto.GetKeyValueStoreRecordResponse) 
        method.invoke(function, responseResult);

    assertThat(result).isNotNull();
    assertThat(result.getContentType()).isEqualTo("application/json");
    assertThat(result.getJsonValue()).isNotNull();
    assertThat(result.getJsonValue().has("array")).isTrue();
    assertThat(result.getJsonValue().has("nested")).isTrue();
    assertThat(result.getTextValue()).isNull();
    assertThat(result.getBase64Value()).isNull();
  }
}