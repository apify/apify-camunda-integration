package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.apify.common.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.WebFetchInput;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Test;

class WebFetchOperationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private JsonNode json(String value) throws Exception {
    return objectMapper.readTree(value);
  }

  @Test
  void buildsBodyWithTrimmedUrlOnly() {
    JsonNode body = ApifyFunction.buildWebFetchRequestBody(
        new WebFetchInput("  https://example.com  ", null, null));

    assertThat(body.toString()).isEqualTo("{\"url\":\"https://example.com\"}");
  }

  @Test
  void omitsBlankFormatsSoWebFetchPicksFromContentType() {
    JsonNode body = ApifyFunction.buildWebFetchRequestBody(
        new WebFetchInput("https://example.com", "  ", null));

    assertThat(body.has("formats")).isFalse();
  }

  @Test
  void normalizesFormats() {
    JsonNode body = ApifyFunction.buildWebFetchRequestBody(
        new WebFetchInput("https://example.com", "Markdown, html ,markdown", null));

    assertThat(body.get("formats").toString()).isEqualTo("[\"markdown\",\"html\"]");
  }

  @Test
  void rejectsUnknownFormat() {
    assertThatThrownBy(() -> ApifyFunction.buildWebFetchRequestBody(
        new WebFetchInput("https://example.com", "markdown,pdf", null)))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("pdf")
        .hasMessageContaining("markdown, html, text, links, raw");
  }

  @Test
  void convertsHeaderValuesToText() throws Exception {
    JsonNode body = ApifyFunction.buildWebFetchRequestBody(new WebFetchInput(
        "https://example.com", null, json("{\"Accept-Language\":\"fr-FR\",\"X-Count\":5,\"X-Debug\":true}")));

    assertThat(body.get("headers").toString())
        .isEqualTo("{\"Accept-Language\":\"fr-FR\",\"X-Count\":\"5\",\"X-Debug\":\"true\"}");
  }

  @Test
  void parsesHeadersGivenAsJsonText() throws Exception {
    JsonNode body = ApifyFunction.buildWebFetchRequestBody(new WebFetchInput(
        "https://example.com", null, json("\"{\\\"Accept-Language\\\":\\\"fr-FR\\\"}\"")));

    assertThat(body.get("headers").toString()).isEqualTo("{\"Accept-Language\":\"fr-FR\"}");
  }

  @Test
  void rejectsHeadersThatAreNotAnObject() throws Exception {
    assertThatThrownBy(() -> ApifyFunction.buildWebFetchRequestBody(new WebFetchInput(
        "https://example.com", null, json("[\"Accept-Language\"]"))))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("JSON object");
  }

  @Test
  void rejectsNestedHeaderValues() throws Exception {
    assertThatThrownBy(() -> ApifyFunction.buildWebFetchRequestBody(new WebFetchInput(
        "https://example.com", null, json("{\"X-Nested\":{\"a\":1}}"))))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("X-Nested");
  }

  @Test
  void masksHeaderValuesWhenLogged() throws Exception {
    var requestInput = new ApifyRequestInput(null, null, null, null, null, new WebFetchInput(
        "https://example.com", "markdown", json("{\"Authorization\":\"Bearer s3cr3t\",\"Cookie\":\"session=abc\"}")));

    // ApifyFunction logs the whole request input, so header values must not appear in toString().
    assertThat(requestInput.toString())
        .doesNotContain("s3cr3t")
        .doesNotContain("session=abc")
        .contains("Authorization=***")
        .contains("Cookie=***")
        .contains("https://example.com");
  }

  @Test
  void masksHeadersGivenAsJsonText() throws Exception {
    var input = new WebFetchInput("https://example.com", null, json("\"{\\\"Authorization\\\":\\\"Bearer s3cr3t\\\"}\""));

    assertThat(input.toString()).doesNotContain("s3cr3t").contains("headers=***");
  }

  @Test
  void rejectsUrlWithoutHost() {
    assertThatThrownBy(() -> ApifyFunction.buildWebFetchRequestBody(
        new WebFetchInput("https://", null, null)))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void failsValidationForNonHttpUrl() throws Exception {
    var input = new ApifyRequest(
        new Authentication("test-token"),
        new Operation("webFetch"),
        new ApifyRequestInput(null, null, null, null, null, new WebFetchInput("ftp://example.com", null, null)));
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();

    assertThatThrownBy(() -> context.bindVariables(ApifyRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("URL must start with http:// or https://");
  }

  @Test
  void failsWhenWebFetchInputIsMissing() throws Exception {
    var input = new ApifyRequest(
        new Authentication("test-token"),
        new Operation("webFetch"),
        new ApifyRequestInput(null, null, null, null, null, null));
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();

    assertThatThrownBy(() -> new ApifyFunction().execute(context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("webFetchInput is null");
  }
}
