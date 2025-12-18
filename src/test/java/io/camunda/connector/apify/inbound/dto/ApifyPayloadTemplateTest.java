package io.camunda.connector.apify.inbound.dto;

import static io.camunda.connector.apify.inbound.InboundTestFixtures.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

/**
 * Unit tests for ApifyPayloadTemplate.
 */
class ApifyPayloadTemplateTest {

    @Nested
    @DisplayName("Template Content")
    class TemplateContent {

        @Test
        void shouldContainRequiredWebhookFields() {
            String content = ApifyPayloadTemplate.getContent();

            assertThat(content).isNotBlank();
            assertThat(content).contains("{{userId}}");
            assertThat(content).contains("{{createdAt}}");
            assertThat(content).contains("{{eventType}}");
            assertThat(content).contains("{{eventData}}");
            assertThat(content).contains("{{resource}}");
        }
    }

    @Nested
    @DisplayName("JSON Structure Validation")
    class JsonStructureValidation {

        @Test
        void shouldBeParsableJsonWhenPlaceholdersReplaced() {
            String content = ApifyPayloadTemplate.getContent();

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
            
            // Verify exactly 5 placeholders
            long placeholderCount = Pattern.compile(Pattern.quote("{{")).matcher(content).results().count();
            assertThat(placeholderCount).isEqualTo(5);
        }
    }
}
