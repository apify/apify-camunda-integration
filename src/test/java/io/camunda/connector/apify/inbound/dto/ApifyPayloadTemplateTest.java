package io.camunda.connector.apify.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ApifyPayloadTemplate.
 */
class ApifyPayloadTemplateTest {

    @Test
    void shouldReturnNonEmptyTemplateContent() {
        // given / when
        String content = ApifyPayloadTemplate.getContent();

        // then
        assertThat(content).isNotNull();
        assertThat(content).isNotBlank();
    }

    @Test
    void shouldContainRequiredWebhookFields() {
        // given / when
        String content = ApifyPayloadTemplate.getContent();

        // then - verify all required Apify webhook template variables are present
        assertThat(content).contains("{{userId}}");
        assertThat(content).contains("{{createdAt}}");
        assertThat(content).contains("{{eventType}}");
        assertThat(content).contains("{{eventData}}");
        assertThat(content).contains("{{resource}}");
    }

    @Test
    void shouldBeValidJsonStructure() {
        // given / when
        String content = ApifyPayloadTemplate.getContent();

        // then - verify JSON structure (without Mustache variables)
        assertThat(content).startsWith("{");
        assertThat(content).endsWith("}");
        assertThat(content).contains("\"userId\":");
        assertThat(content).contains("\"createdAt\":");
        assertThat(content).contains("\"eventType\":");
        assertThat(content).contains("\"eventData\":");
        assertThat(content).contains("\"resource\":");
    }

    @Test
    void shouldReturnSameContentOnMultipleCalls() {
        // given / when
        String content1 = ApifyPayloadTemplate.getContent();
        String content2 = ApifyPayloadTemplate.getContent();

        // then
        assertThat(content1).isEqualTo(content2);
    }
}
