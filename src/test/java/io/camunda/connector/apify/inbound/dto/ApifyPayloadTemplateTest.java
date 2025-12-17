package io.camunda.connector.apify.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApifyPayloadTemplate.
 */
class ApifyPayloadTemplateTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("Template Content")
    class TemplateContent {

        @Test
        void shouldReturnNonEmptyTemplateContent() {
            String content = ApifyPayloadTemplate.getContent();

            assertThat(content).isNotNull();
            assertThat(content).isNotBlank();
        }

        @Test
        void shouldContainRequiredWebhookFields() {
            String content = ApifyPayloadTemplate.getContent();

            assertThat(content).contains("{{userId}}");
            assertThat(content).contains("{{createdAt}}");
            assertThat(content).contains("{{eventType}}");
            assertThat(content).contains("{{eventData}}");
            assertThat(content).contains("{{resource}}");
        }

        @Test
        void shouldReturnSameContentOnMultipleCalls() {
            String content1 = ApifyPayloadTemplate.getContent();
            String content2 = ApifyPayloadTemplate.getContent();

            assertThat(content1).isEqualTo(content2);
        }
    }

    @Nested
    @DisplayName("JSON Structure Validation")
    class JsonStructureValidation {

        @Test
        void shouldBeValidJsonStructure() {
            String content = ApifyPayloadTemplate.getContent();

            assertThat(content).startsWith("{");
            assertThat(content).endsWith("}");
            assertThat(content).contains("\"userId\":");
            assertThat(content).contains("\"createdAt\":");
            assertThat(content).contains("\"eventType\":");
            assertThat(content).contains("\"eventData\":");
            assertThat(content).contains("\"resource\":");
        }

        @Test
        void shouldBeParsableJsonWhenPlaceholdersReplaced() {
            String content = ApifyPayloadTemplate.getContent();

            // Replace Apify placeholders with valid JSON values
            String jsonWithDummies = content
                    .replace("{{userId}}", "\"user123\"")
                    .replace("{{createdAt}}", "\"2024-01-15T10:30:00.000Z\"")
                    .replace("{{eventType}}", "\"ACTOR.RUN.SUCCEEDED\"")
                    .replace("{{eventData}}", "{\"key\": \"value\"}")
                    .replace("{{resource}}", "{\"id\": \"run123\"}");

            assertThatCode(() -> OBJECT_MAPPER.readTree(jsonWithDummies))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldBeParsableJsonWithNullPlaceholders() {
            String content = ApifyPayloadTemplate.getContent();

            // Test with null values (valid JSON)
            String jsonWithNulls = content
                    .replace("{{userId}}", "null")
                    .replace("{{createdAt}}", "null")
                    .replace("{{eventType}}", "\"ACTOR.RUN.SUCCEEDED\"")
                    .replace("{{eventData}}", "null")
                    .replace("{{resource}}", "null");

            assertThatCode(() -> OBJECT_MAPPER.readTree(jsonWithNulls))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldHaveCorrectJsonKeyStructure() throws Exception {
            String content = ApifyPayloadTemplate.getContent();

            // Replace with valid JSON to parse
            String jsonWithDummies = content
                    .replace("{{userId}}", "\"user123\"")
                    .replace("{{createdAt}}", "\"2024-01-15T10:30:00.000Z\"")
                    .replace("{{eventType}}", "\"ACTOR.RUN.SUCCEEDED\"")
                    .replace("{{eventData}}", "{}")
                    .replace("{{resource}}", "{}");

            var jsonNode = OBJECT_MAPPER.readTree(jsonWithDummies);

            assertThat(jsonNode.has("userId")).isTrue();
            assertThat(jsonNode.has("createdAt")).isTrue();
            assertThat(jsonNode.has("eventType")).isTrue();
            assertThat(jsonNode.has("eventData")).isTrue();
            assertThat(jsonNode.has("resource")).isTrue();
        }

        @Test
        void shouldHaveExactlyFiveTopLevelFields() throws Exception {
            String content = ApifyPayloadTemplate.getContent();

            String jsonWithDummies = content
                    .replace("{{userId}}", "\"user123\"")
                    .replace("{{createdAt}}", "\"2024-01-15\"")
                    .replace("{{eventType}}", "\"ACTOR.RUN.SUCCEEDED\"")
                    .replace("{{eventData}}", "{}")
                    .replace("{{resource}}", "{}");

            var jsonNode = OBJECT_MAPPER.readTree(jsonWithDummies);

            assertThat(jsonNode.size()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Apify Template Format")
    class ApifyTemplateFormat {

        @Test
        void shouldUseDoubleBracesForPlaceholders() {
            String content = ApifyPayloadTemplate.getContent();

            // Apify uses {{placeholder}} format
            assertThat(content).doesNotContain("${");
            assertThat(content).doesNotContain("{{{");

            // Count opening and closing braces should be balanced
            long openingCount = content.chars().filter(ch -> ch == '{').count();
            long closingCount = content.chars().filter(ch -> ch == '}').count();
            assertThat(openingCount).isEqualTo(closingCount);
        }

        @Test
        void shouldHaveBalancedPlaceholderBraces() {
            String content = ApifyPayloadTemplate.getContent();

            // Count {{ and }} occurrences
            int doubleOpenCount = countOccurrences(content, "{{");
            int doubleCloseCount = countOccurrences(content, "}}");

            assertThat(doubleOpenCount).isEqualTo(doubleCloseCount);
            assertThat(doubleOpenCount).isEqualTo(5); // userId, createdAt, eventType, eventData, resource
        }

        private int countOccurrences(String str, String sub) {
            int count = 0;
            int idx = 0;
            while ((idx = str.indexOf(sub, idx)) != -1) {
                count++;
                idx += sub.length();
            }
            return count;
        }
    }
}
