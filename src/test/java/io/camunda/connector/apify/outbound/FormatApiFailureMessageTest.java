package io.camunda.connector.apify.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.apify.common.ApifyApiException;
import org.junit.jupiter.api.Test;

class FormatApiFailureMessageTest {

    @Test
    void invalidInput_actorWithSlug_includesInputSchemaUrl() {
        var ex = new ApifyApiException("msg", 400, "invalid-input", "Missing field 'url'");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Actor", "apify~web-scraper");

        assertThat(msg).contains("rejected the input");
        assertThat(msg).contains("Missing field 'url'");
        assertThat(msg).contains("Custom Input JSON");
        assertThat(msg).contains("https://apify.com/apify/web-scraper/input-schema");
    }

    @Test
    void invalidInput_actorWithRawId_omitsInputSchemaUrl() {
        var ex = new ApifyApiException("msg", 400, "invalid-input", "Bad input");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Actor", "aYG0l9s7dbB7j3gbS");

        assertThat(msg).contains("rejected the input");
        assertThat(msg).contains("Custom Input JSON");
        assertThat(msg).doesNotContain("https://apify.com/");
    }

    @Test
    void invalidInput_task_neverIncludesInputSchemaUrl() {
        var ex = new ApifyApiException("msg", 400, "invalid-input", "Bad input");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Task", "user~my-task");

        assertThat(msg).contains("Task");
        assertThat(msg).contains("rejected the input");
        assertThat(msg).doesNotContain("input-schema");
    }

    @Test
    void invalidInput_nullApifyMessage_fallsBackToGeneric() {
        var ex = new ApifyApiException("msg", 400, "invalid-input", null);

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Actor", "apify~scraper");

        assertThat(msg).contains("invalid input");
        assertThat(msg).contains("Custom Input JSON");
    }

    @Test
    void nonInvalidInput_returnsExceptionMessage() {
        var ex = new ApifyApiException("Apify API error (404): Actor not found", 404, "not-found", "Actor not found");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Actor", "apify~scraper");

        assertThat(msg).isEqualTo("Apify API error (404): Actor not found");
    }

    @Test
    void invalidInput_actorWithSlash_includesInputSchemaUrl() {
        var ex = new ApifyApiException("msg", 400, "invalid-input", "Bad input");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Actor", "apify/web-scraper");

        assertThat(msg).contains("https://apify.com/apify/web-scraper/input-schema");
    }

    @Test
    void invalidInput_dataset_returnsExceptionMessageWithoutHint() {
        // The "Custom Input JSON" hint only applies to Actor/Task. For other resources,
        // we fall back to the exception's clean message.
        var ex = new ApifyApiException("Apify API error (400 invalid-input): bad offset",
                400, "invalid-input", "bad offset");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Dataset", "abc123");

        assertThat(msg).isEqualTo("Apify API error (400 invalid-input): bad offset");
        assertThat(msg).doesNotContain("Custom Input JSON");
        assertThat(msg).doesNotContain("input-schema");
    }

    @Test
    void invalidInput_keyValueStore_returnsExceptionMessageWithoutHint() {
        var ex = new ApifyApiException("Apify API error (404 record-not-found): not found",
                404, "record-not-found", "not found");

        String msg = ApifyFunction.formatApiFailureMessage(ex, "Key-value store record", "store/key");

        assertThat(msg).isEqualTo("Apify API error (404 record-not-found): not found");
        assertThat(msg).doesNotContain("Custom Input JSON");
    }
}
