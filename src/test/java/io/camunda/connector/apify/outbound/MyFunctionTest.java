package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorInput;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class MyFunctionTest {

  ObjectMapper objectMapper = new ObjectMapper();

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
}