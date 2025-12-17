package io.camunda.connector.apify.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApifyPayloadTemplate.
 */
class ApifyPayloadTemplateTest {

    @Test
    void shouldReturnNonEmptyTemplateContent() {
        String content = ApifyPayloadTemplate.getContent();

        assertThat(content).isNotNull();
        assertThat(content).isNotBlank();
    }

    /**
     * Tests that the template contains all required webhook fields.
     */
    @Test
    void shouldContainRequiredWebhookFields() {
        String content = ApifyPayloadTemplate.getContent();

        assertThat(content).contains("{{userId}}");
        assertThat(content).contains("{{createdAt}}");
        assertThat(content).contains("{{eventType}}");
        assertThat(content).contains("{{eventData}}");
        assertThat(content).contains("{{resource}}");
    }

    /**
     * Tests that the template is a valid JSON structure.
     */
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

    /**
     * Tests that the template content is the same on multiple calls.
     */
    @Test
    void shouldReturnSameContentOnMultipleCalls() {
        String content1 = ApifyPayloadTemplate.getContent();
        String content2 = ApifyPayloadTemplate.getContent();

        assertThat(content1).isEqualTo(content2);
    }
}
