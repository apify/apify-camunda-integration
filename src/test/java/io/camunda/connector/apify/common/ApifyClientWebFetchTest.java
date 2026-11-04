package io.camunda.connector.apify.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApifyClientWebFetchTest {

  private static final String REQUEST_BODY = "{\"url\":\"https://example.com\"}";

  private record RecordedRequest(
      String method, String path, String authorization, String platform, String contentType, String body) {}

  private HttpServer server;
  private final AtomicInteger requestCount = new AtomicInteger();
  private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
  private volatile int responseStatus = 200;
  private volatile String responseBody = "{}";

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requestCount.incrementAndGet();
      var headers = exchange.getRequestHeaders();
      lastRequest.set(new RecordedRequest(
          exchange.getRequestMethod(),
          exchange.getRequestURI().getPath(),
          headers.getFirst("Authorization"),
          headers.getFirst("x-apify-integration-platform"),
          headers.getFirst("Content-Type"),
          new String(exchange.getRequestBody().readAllBytes(), UTF_8)));
      byte[] bytes = responseBody.getBytes(UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
      exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        exchange.getResponseBody().write(bytes);
      }
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private ApifyClient client() {
    return new ApifyClient("http://127.0.0.1:" + server.getAddress().getPort() + "/");
  }

  @Test
  void postsBodyWithTokenAndPlatformHeader() throws IOException {
    responseBody = "{\"url\":\"https://example.com\",\"markdown\":\"# Example Domain\"}";

    try (var client = client()) {
      var result = client.webFetch("test-token", REQUEST_BODY);

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getResponseBody()).isEqualTo(responseBody);
    }

    var request = lastRequest.get();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.path()).isEqualTo("/");
    assertThat(request.authorization()).isEqualTo("Bearer test-token");
    assertThat(request.platform()).isEqualTo("camunda");
    assertThat(request.contentType()).startsWith("application/json");
    assertThat(request.body()).isEqualTo(REQUEST_BODY);
  }

  @Test
  void returnsTargetErrorPageAsContent() throws IOException {
    // Web Fetch answers 200 and reports the target page's own status in fetch.httpStatusCode.
    responseBody = "{\"url\":\"https://example.com/missing\",\"fetch\":{\"httpStatusCode\":404}}";

    try (var client = client()) {
      var result = client.webFetch("test-token", REQUEST_BODY);

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getResponseBody()).contains("\"httpStatusCode\":404");
    }
  }

  @Test
  void reportsActorErrorWithCode() throws IOException {
    responseStatus = 400;
    responseBody = "{\"error\":\"URL is not valid\",\"code\":\"INVALID_URL\"}";

    try (var client = client()) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(400);
            assertThat(e.getMessage())
                .isEqualTo("Web Fetch failed with status 400: URL is not valid (code: INVALID_URL)");
          });
    }
  }

  @Test
  void reportsGatewayErrorEnvelope() throws IOException {
    responseStatus = 401;
    responseBody = "{\"error\":{\"type\":\"api-token-missing\",\"message\":\"This is a standby Actor.\"}}";

    try (var client = client()) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(401);
            assertThat(e.getErrorType()).isEqualTo("api-token-missing");
            assertThat(e.getMessage())
                .isEqualTo("Web Fetch failed with status 401: This is a standby Actor.");
          });
    }
  }

  @Test
  void doesNotRetryServerErrors() throws IOException {
    // A retry could bill the same fetch twice, so a 5xx fails on the first attempt.
    responseStatus = 502;
    responseBody = "Bad Gateway";

    try (var client = client()) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(502);
            assertThat(e.getMessage()).isEqualTo("Web Fetch failed with status 502: Bad Gateway");
          });
    }
    assertThat(requestCount.get()).isEqualTo(1);
  }

  @Test
  void doesNotReplayServiceUnavailable() throws IOException {
    // Apache HttpClient's default strategy replays a POST once on 503; Web Fetch must not.
    responseStatus = 503;
    responseBody = "{\"error\":{\"type\":\"service-unavailable\",\"message\":\"Try again later.\"}}";

    try (var client = client()) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> assertThat(e.getStatusCode()).isEqualTo(503));
    }
    assertThat(requestCount.get()).isEqualTo(1);
  }

  @Test
  void doesNotReplayRateLimit() throws IOException {
    responseStatus = 429;
    responseBody = "{\"error\":{\"type\":\"rate-limit-exceeded\",\"message\":\"Too many requests.\"}}";

    try (var client = client()) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> assertThat(e.getStatusCode()).isEqualTo(429));
    }
    assertThat(requestCount.get()).isEqualTo(1);
  }

  @Test
  void reportsConnectionFailure() throws IOException {
    var client = client();
    server.stop(0);

    try (client) {
      assertThatThrownBy(() -> client.webFetch("test-token", REQUEST_BODY))
          .isInstanceOfSatisfying(ApifyApiException.class, e -> {
            assertThat(e.getStatusCode()).isZero();
            assertThat(e.getMessage()).startsWith("Could not connect to Web Fetch at http://127.0.0.1:");
          });
    }
  }
}
