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
      new ApifyRequestInput(new RunActorInput("actor123"), null)
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
      .isEqualTo("Operation type: runActor Authentication: Authentication[token=testToken] Apify Request Input: ApifyRequestInput[runActorInput=RunActorInput[actorId=actor123], runTaskInput=null]");
  }

  @Test
  void shouldExecuteSuccessfullyWithValidInput() throws Exception {
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
      new Operation("runActor"), 
      new ApifyRequestInput(new RunActorInput("actor123"), null)
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
        .isEqualTo("Operation type: runActor Authentication: Authentication[token=testToken] Apify Request Input: ApifyRequestInput[runActorInput=RunActorInput[actorId=actor123], runTaskInput=null]");
  }
}