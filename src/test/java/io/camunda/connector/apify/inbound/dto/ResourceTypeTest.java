package io.camunda.connector.apify.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for ResourceType enum.
 */
class ResourceTypeTest {

    // ==================== getValue() Tests ====================

    @Test
    void shouldReturnCorrectValueForActor() {
        // given / when / then
        assertThat(ResourceType.ACTOR.getValue()).isEqualTo("actor");
    }

    @Test
    void shouldReturnCorrectValueForTask() {
        // given / when / then
        assertThat(ResourceType.TASK.getValue()).isEqualTo("task");
    }

    // ==================== getConditionKey() Tests ====================

    @Test
    void shouldReturnCorrectConditionKeyForActor() {
        // given / when / then
        assertThat(ResourceType.ACTOR.getConditionKey()).isEqualTo("actorId");
    }

    @Test
    void shouldReturnCorrectConditionKeyForTask() {
        // given / when / then
        assertThat(ResourceType.TASK.getConditionKey()).isEqualTo("actorTaskId");
    }

    // ==================== fromValue() Tests ====================

    @ParameterizedTest
    @CsvSource({
            "actor, ACTOR",
            "task, TASK"
    })
    void shouldParseValidResourceTypeValues(String value, ResourceType expected) {
        // given / when
        ResourceType result = ResourceType.fromValue(value);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ACTOR", "TASK", "Actor", "Task", "invalid", "actors", "", " " })
    void shouldThrowExceptionForInvalidValues(String invalidValue) {
        // given / when / then
        assertThatThrownBy(() -> ResourceType.fromValue(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resource type");
    }

    @ParameterizedTest
    @NullSource
    void shouldThrowExceptionForNullValue(String nullValue) {
        // given / when / then
        assertThatThrownBy(() -> ResourceType.fromValue(nullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    // ==================== Enum Consistency Tests ====================

    @Test
    void shouldHaveExactlyTwoResourceTypes() {
        // given / when / then
        assertThat(ResourceType.values()).hasSize(2);
    }

    @Test
    void shouldRoundTripThroughFromValue() {
        // given / when / then
        for (ResourceType type : ResourceType.values()) {
            assertThat(ResourceType.fromValue(type.getValue())).isEqualTo(type);
        }
    }
}
