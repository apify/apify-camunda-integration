package io.camunda.connector.apify.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApifyApiExceptionTest {

    @Test
    void fromApiResponse_parsesApifyErrorEnvelope() {
        String body = """
                {"error":{"type":"invalid-input","message":"Missing required field 'url'"}}
                """;
        ApifyApiException ex = ApifyApiException.fromApiResponse(400, body);

        assertThat(ex.getStatusCode()).isEqualTo(400);
        assertThat(ex.getErrorType()).isEqualTo("invalid-input");
        assertThat(ex.getApifyErrorMessage()).isEqualTo("Missing required field 'url'");
        assertThat(ex.isInvalidInput()).isTrue();
        assertThat(ex.getMessage()).contains("400").contains("invalid-input").contains("Missing required field");
    }

    @Test
    void fromApiResponse_parsesEnvelopeWithTypeOnly() {
        String body = """
                {"error":{"type":"rate-limit-exceeded"}}
                """;
        ApifyApiException ex = ApifyApiException.fromApiResponse(429, body);

        assertThat(ex.getErrorType()).isEqualTo("rate-limit-exceeded");
        assertThat(ex.getApifyErrorMessage()).isNull();
        assertThat(ex.getMessage()).contains("429");
    }

    @Test
    void fromApiResponse_fallsBackForNonJsonBody() {
        ApifyApiException ex = ApifyApiException.fromApiResponse(502, "Bad Gateway");

        assertThat(ex.getStatusCode()).isEqualTo(502);
        assertThat(ex.getErrorType()).isNull();
        assertThat(ex.getApifyErrorMessage()).isNull();
        assertThat(ex.getMessage()).contains("502").contains("Bad Gateway");
    }

    @Test
    void fromApiResponse_fallsBackForMalformedJson() {
        ApifyApiException ex = ApifyApiException.fromApiResponse(500, "{not json");

        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getErrorType()).isNull();
        assertThat(ex.getMessage()).contains("500");
    }

    @Test
    void fromApiResponse_handlesNullBody() {
        ApifyApiException ex = ApifyApiException.fromApiResponse(500, null);

        assertThat(ex.getStatusCode()).isEqualTo(500);
        assertThat(ex.getErrorType()).isNull();
        assertThat(ex.getMessage()).contains("500");
    }

    @Test
    void fromApiResponse_handlesEmptyBody() {
        ApifyApiException ex = ApifyApiException.fromApiResponse(404, "   ");

        assertThat(ex.getStatusCode()).isEqualTo(404);
        assertThat(ex.getMessage()).contains("404");
    }

    @Test
    void fromApiResponse_truncatesLongBody() {
        String longBody = "x".repeat(500);
        ApifyApiException ex = ApifyApiException.fromApiResponse(500, longBody);

        assertThat(ex.getMessage()).contains("...");
        assertThat(ex.getMessage().length()).isLessThan(400);
    }

    @Test
    void fromApiResponse_jsonWithoutErrorField() {
        String body = """
                {"data":{"id":"abc123"}}
                """;
        ApifyApiException ex = ApifyApiException.fromApiResponse(500, body);

        assertThat(ex.getErrorType()).isNull();
        assertThat(ex.getApifyErrorMessage()).isNull();
        assertThat(ex.getMessage()).contains("500").contains("abc123");
    }

    @Test
    void isInvalidInput_falseForOtherTypes() {
        ApifyApiException ex = new ApifyApiException("msg", 404, "not-found", "Actor not found");

        assertThat(ex.isInvalidInput()).isFalse();
    }
}
