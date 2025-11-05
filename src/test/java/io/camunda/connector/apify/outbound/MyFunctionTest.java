package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
            new Operation("runActor"),
            new ApifyRequestInput(
                new RunActorInput("test-actor-id"),
                null,
                null
            )
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
      .variables(objectMapper.writeValueAsString(input))
      .build();
    // when
    var result = function.execute(context);
    // then
    assertThat(result)
      .isInstanceOf(ApifyResult.class)
      .extracting("myProperty")
      .asString()
      .contains("RunActor operation");
  }

  @Test
  void shouldReturnResultForUnsupportedOperation() throws Exception {
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
            new Operation("unsupportedOperation"),
            new ApifyRequestInput(
                null,
                null,
                null
            )
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();
    // when
    var result = function.execute(context);
    // then
    assertThat(result)
        .isInstanceOf(ApifyResult.class)
        .extracting("myProperty")
        .asString()
        .contains("Unsupported operation type");
  }
}