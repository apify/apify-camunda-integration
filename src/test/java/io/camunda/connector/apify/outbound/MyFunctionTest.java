package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorInput;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

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
}