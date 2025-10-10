package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.camunda.connector.apify.outbound.ApifyFunction;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.ApifyRequest;
import io.camunda.connector.apify.outbound.ApifyResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

import org.junit.jupiter.api.Test;

public class MyFunctionTest {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReturnReceivedMessageWhenExecute() throws Exception {
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
            new Operation("runActor"), null
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
      .extracting("operationType")
      .isEqualTo("runActor");
  }

  @Test
  void shouldThrowWithErrorCodeWhenMessageStartsWithFail() throws Exception {
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
            new Operation("runActor"), null
    );
    var function = new ApifyFunction();
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();
    // when
    var result = catchThrowable(() -> function.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("started with 'runActor'")
        .extracting("errorCode").isEqualTo("FAIL");
  }
}