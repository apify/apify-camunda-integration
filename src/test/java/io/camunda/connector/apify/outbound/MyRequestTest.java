package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.RunActorInput;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;

import org.junit.jupiter.api.Test;

public class MyRequestTest {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReplaceTokenSecretWhenReplaceSecrets() throws JsonProcessingException {
    // given
    var input = new ApifyRequest(
      new Authentication("secrets.MY_TOKEN"),
      new Operation("runActor"),
      new ApifyRequestInput(
          new RunActorInput("test-actor-id"),
          null,
          null
      )
    );
    var context = OutboundConnectorContextBuilder.create()
      .secret("MY_TOKEN", "token value")
            .variables(objectMapper.writeValueAsString(input))
      .build();
    // when
    final var connectorRequest = context.bindVariables(ApifyRequest.class);
    // then
    assertThat(connectorRequest)
      .extracting("authentication")
      .extracting("token")
      .isEqualTo("token value");
  }

  @Test
  void shouldFailWhenValidate_NoAuthentication() throws JsonProcessingException {
    // given
    var input = new ApifyRequest(
      null,
      new Operation("runActor"),
      new ApifyRequestInput(
          new RunActorInput("test-actor-id"),
          null,
          null
      )
    );
    var context = OutboundConnectorContextBuilder.create().variables(objectMapper.writeValueAsString(input)).build();
    // when
    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
      // then
      .isInstanceOf(ConnectorInputException.class)
      .hasMessageContaining("authentication");
  }

  @Test
  void shouldFailWhenValidate_NoToken() throws JsonProcessingException {
    // given
    var input = new ApifyRequest(
      new Authentication(null),
      new Operation("runActor"),
      new ApifyRequestInput(
          new RunActorInput("test-actor-id"),
          null,
          null
      )
    );
    var context = OutboundConnectorContextBuilder.create().variables(objectMapper.writeValueAsString(input)).build();
    // when
    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
      // then
      .isInstanceOf(ConnectorInputException.class)
      .hasMessageContaining("token");
  }

  @Test
  void shouldFailWhenValidate_NoMesage() throws JsonProcessingException {
    // given
    var input = new ApifyRequest(
      new Authentication("testToken"),
      new Operation("runActor"),
      null
    );
    var context = OutboundConnectorContextBuilder.create().variables(objectMapper.writeValueAsString(input)).build();
    // when
    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
      // then
      .isInstanceOf(ConnectorInputException.class)
      .hasMessageContaining("apifyRequestInput");
  }

  @Test
  void shouldFailWhenValidate_TokenEmpty() throws JsonProcessingException {
    // given
    var input = new ApifyRequest(
      new Authentication(""),
      new Operation("runActor"),
      new ApifyRequestInput(
          new RunActorInput("test-actor-id"),
          null,
          null
      )
    );
    var context = OutboundConnectorContextBuilder.create().variables(objectMapper.writeValueAsString(input)).build();
    // when
    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
      // then
      .isInstanceOf(ConnectorInputException.class)
      .hasMessageContaining("authentication.token: Validation failed");
  }
}