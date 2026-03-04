package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorRequest;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;

import org.junit.jupiter.api.Test;

public class ApifyRequestTest {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReplaceTokenSecretWhenReplaceSecrets() throws JsonProcessingException {

    var runActorInput = new RunActorRequest(
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
      new Authentication("secrets.MY_TOKEN"),
      new Operation("runActor"),
      apifyRequestInput
    );
    var context = OutboundConnectorContextBuilder.create()
      .secret("MY_TOKEN", "token value")
            .variables(objectMapper.writeValueAsString(input))
      .build();

    final var connectorRequest = context.bindVariables(ApifyRequest.class);

    assertThat(connectorRequest.authentication().token()).isEqualTo("token value");
  }

  @Test
  void shouldFailValidationWhenAuthTokenIsNull() throws JsonProcessingException {
    var runActorInput = new RunActorRequest(
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
      new Authentication(null),
      new Operation("runActor"),
      apifyRequestInput
    );
    var context = OutboundConnectorContextBuilder.create()
      .variables(objectMapper.writeValueAsString(input))
      .build();

    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
      .isInstanceOf(ConnectorInputException.class);
  }
}
