package io.camunda.connector.apify.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

class ApifyClientTest {

    private CloseableHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(CloseableHttpClient.class);
    }

    private ApifyClient createClient() {
        return new ApifyClient("test-token", mockHttpClient);
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int statusCode, String body, String contentType) throws IOException {
        when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
            .thenAnswer(invocation -> {
                HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                when(response.getCode()).thenReturn(statusCode);
                if (body != null) {
                    var entity = new StringEntity(body, ContentType.parse(contentType));
                    when(response.getEntity()).thenReturn(entity);
                } else {
                    when(response.getEntity()).thenReturn(null);
                }
                return handler.handleResponse(response);
            });
    }

    @SuppressWarnings("unchecked")
    private void stubIOException() throws IOException {
        when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
            .thenThrow(new IOException("Connection refused"));
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorTests {

        @Test
        void shouldThrowOnNullAuthToken() {
            assertThatThrownBy(() -> new ApifyClient(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("authToken must not be null");
        }

        @Test
        void shouldCreateClientWithValidToken() {
            try (var client = new ApifyClient("valid-token", mockHttpClient)) {
                assertThat(client).isNotNull();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    @DisplayName("Request Headers")
    class HeaderTests {

        @SuppressWarnings("unchecked")
        @Test
        void shouldIncludeAuthorizationAndPlatformHeaders() throws Exception {
            stubResponse(200, "{}", "application/json");

            try (var client = createClient()) {
                client.getActor("test-actor");
            }

            ArgumentCaptor<ClassicHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
            verify(mockHttpClient).execute(isNull(), requestCaptor.capture(), any(HttpClientResponseHandler.class));

            ClassicHttpRequest request = requestCaptor.getValue();
            assertThat(request.getHeader("Authorization").getValue()).isEqualTo("Bearer test-token");
            assertThat(request.getHeader("x-apify-integration-platform").getValue()).isEqualTo("camunda");
        }
    }

    @Nested
    @DisplayName("Successful Responses")
    class SuccessTests {

        @Test
        void shouldReturnResponseForGetActor() throws IOException {
            String expectedBody = "{\"data\":{\"id\":\"actor123\"}}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test-actor");
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldReturnResponseForRunActor() throws IOException {
            String expectedBody = "{\"data\":{\"id\":\"run123\"}}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.runActor(
                    "test-actor", "{\"key\":\"value\"}", new RunOptions(null, null, null, null));
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldReturnResponseForRunTask() throws IOException {
            String expectedBody = "{\"data\":{\"id\":\"run456\"}}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.runTask(
                    "test-task", null, new RunOptions(60, "512", null, null));
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldReturnResponseForGetDatasetItems() throws IOException {
            String expectedBody = "[{\"title\":\"item1\"}]";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getDatasetItems("ds123", 0, 10);
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldReturnResponseForGetKeyValueStoreRecord() throws IOException {
            String expectedBody = "{\"key\":\"value\"}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getKeyValueStoreRecord("store123", "myKey");
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldHandleNullEntityInResponse() throws IOException {
            stubResponse(204, null, null);

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test-actor");
                assertThat(result.statusCode()).isEqualTo(204);
                assertThat(result.responseBody()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorTests {

        @Test
        void shouldThrowApifyClientExceptionFor400() throws IOException {
            stubResponse(400, "{\"error\":\"bad request\"}", "application/json");

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(ApifyClientException.class)
                    .satisfies(e -> {
                        ApifyClientException ace = (ApifyClientException) e;
                        assertThat(ace.getStatusCode()).isEqualTo(400);
                        assertThat(ace.isLikelyUserError()).isTrue();
                    });
            }
        }

        @Test
        void shouldThrowApifyClientExceptionFor401() throws IOException {
            stubResponse(401, "{\"error\":\"unauthorized\"}", "application/json");

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(ApifyClientException.class)
                    .satisfies(e -> {
                        ApifyClientException ace = (ApifyClientException) e;
                        assertThat(ace.getStatusCode()).isEqualTo(401);
                        assertThat(ace.isLikelyUserError()).isTrue();
                    });
            }
        }

        @Test
        void shouldThrowApifyClientExceptionFor404() throws IOException {
            stubResponse(404, "{\"error\":\"not found\"}", "application/json");

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(ApifyClientException.class)
                    .satisfies(e -> {
                        ApifyClientException ace = (ApifyClientException) e;
                        assertThat(ace.getStatusCode()).isEqualTo(404);
                        assertThat(ace.isLikelyUserError()).isTrue();
                    });
            }
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryTests {

        @SuppressWarnings("unchecked")
        @Test
        void shouldRetryOnServerError500() throws IOException {
            // First two calls: 500, third call: 200
            when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(500);
                    var entity = new StringEntity("server error", ContentType.TEXT_PLAIN);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                })
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(200);
                    var entity = new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                });

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test-actor");
                assertThat(result.statusCode()).isEqualTo(200);
            }

            verify(mockHttpClient, times(2)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldRetryOnRateLimitError429() throws IOException {
            when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(429);
                    var entity = new StringEntity("rate limited", ContentType.TEXT_PLAIN);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                })
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(200);
                    var entity = new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                });

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test-actor");
                assertThat(result.statusCode()).isEqualTo(200);
            }

            verify(mockHttpClient, times(2)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldNotRetryOnClientError400() throws IOException {
            stubResponse(400, "bad request", "text/plain");

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(ApifyClientException.class);
            }

            verify(mockHttpClient, times(1)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldExhaustRetriesOnPersistentServerError() throws IOException {
            when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(500);
                    var entity = new StringEntity("server error", ContentType.TEXT_PLAIN);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                });

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(ApifyClientException.class)
                    .satisfies(e -> assertThat(((ApifyClientException) e).getStatusCode()).isEqualTo(500));
            }

            verify(mockHttpClient, times(3)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldRetryOnIOException() throws IOException {
            when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenThrow(new IOException("Connection refused"))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<ApifyClient.ResponseResult> handler = invocation.getArgument(2);
                    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
                    when(response.getCode()).thenReturn(200);
                    var entity = new StringEntity("{\"ok\":true}", ContentType.APPLICATION_JSON);
                    when(response.getEntity()).thenReturn(entity);
                    return handler.handleResponse(response);
                });

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test-actor");
                assertThat(result.statusCode()).isEqualTo(200);
            }

            verify(mockHttpClient, times(2)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        void shouldExhaustRetriesOnPersistentIOException() throws IOException {
            when(mockHttpClient.execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenThrow(new IOException("Connection refused"));

            try (var client = createClient()) {
                assertThatThrownBy(() -> client.getActor("test-actor"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Connection refused");
            }

            verify(mockHttpClient, times(3)).execute(isNull(), any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        }
    }

    @Nested
    @DisplayName("Webhook Operations")
    class WebhookTests {

        @Test
        void shouldCreateWebhook() throws IOException {
            String expectedBody = "{\"data\":{\"id\":\"wh123\"}}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.createWebhook("{\"requestUrl\":\"https://example.com\"}");
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }

        @Test
        void shouldDeleteWebhook() throws IOException {
            stubResponse(200, "{}", "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.deleteWebhook("wh123");
                assertThat(result.statusCode()).isEqualTo(200);
            }
        }

        @Test
        void shouldTreatDeleteWebhook404AsSuccess() throws IOException {
            stubResponse(404, "not found", "text/plain");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.deleteWebhook("wh123");
                assertThat(result.statusCode()).isEqualTo(404);
            }
        }

        @Test
        void shouldListWebhooks() throws IOException {
            String expectedBody = "{\"data\":{\"items\":[]}}";
            stubResponse(200, expectedBody, "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.listWebhooks();
                assertThat(result.statusCode()).isEqualTo(200);
                assertThat(result.responseBody()).isEqualTo(expectedBody);
            }
        }
    }

    @Nested
    @DisplayName("Run Status with waitForFinish")
    class RunStatusTests {

        @Test
        void shouldPassWaitForFinishParameter() throws IOException {
            stubResponse(200, "{\"data\":{\"status\":\"RUNNING\"}}", "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getRunStatus("run123", 30);
                assertThat(result.statusCode()).isEqualTo(200);
            }
        }

        @Test
        void shouldOmitWaitForFinishWhenNull() throws IOException {
            stubResponse(200, "{\"data\":{\"status\":\"SUCCEEDED\"}}", "application/json");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getRunStatus("run123", null);
                assertThat(result.statusCode()).isEqualTo(200);
            }
        }
    }

    @Nested
    @DisplayName("AutoCloseable")
    class CloseTests {

        @Test
        void shouldCloseHttpClient() throws IOException {
            var client = createClient();
            client.close();
            verify(mockHttpClient).close();
        }
    }

    @Nested
    @DisplayName("Content-Type Handling")
    class ContentTypeTests {

        @Test
        void shouldExtractContentTypeWithoutCharset() throws IOException {
            stubResponse(200, "{}", "application/json; charset=utf-8");

            try (var client = createClient()) {
                ApifyClient.ResponseResult result = client.getActor("test");
                assertThat(result.contentType()).isEqualTo("application/json");
            }
        }
    }
}
