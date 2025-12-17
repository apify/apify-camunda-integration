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

    /**
     * Tests that the getValue() method returns the correct value for the ACTOR
     * enum.
     */
    @Test
    void shouldReturnCorrectValueForActor() {
        assertThat(ResourceType.ACTOR.getValue()).isEqualTo("actor");
    }

    /**
     * Tests that the getValue() method returns the correct value for the TASK
     * enum.
     */
    @Test
    void shouldReturnCorrectValueForTask() {
        assertThat(ResourceType.TASK.getValue()).isEqualTo("task");
    }

    // ==================== getConditionKey() Tests ====================

    /**
     * Tests that the getConditionKey() method returns the correct condition key
     * for the ACTOR enum.
     */
    @Test
    void shouldReturnCorrectConditionKeyForActor() {
        assertThat(ResourceType.ACTOR.getConditionKey()).isEqualTo("actorId");
    }

    /**
     * Tests that the getConditionKey() method returns the correct condition key
     * for the TASK enum.
     */
    @Test
    void shouldReturnCorrectConditionKeyForTask() {
        assertThat(ResourceType.TASK.getConditionKey()).isEqualTo("actorTaskId");
    }

    // ==================== fromValue() Tests ====================

    /**
     * Tests that the fromValue() method returns the correct ResourceType for
     * valid values.
     */
    @ParameterizedTest
    @CsvSource({
            "actor, ACTOR",
            "task, TASK"
    })
    void shouldParseValidResourceTypeValues(String value, ResourceType expected) {
        ResourceType result = ResourceType.fromValue(value);

        assertThat(result).isEqualTo(expected);
    }

    /**
     * Tests that the fromValue() method throws an exception for invalid values.
     */
    @ParameterizedTest
    @ValueSource(strings = { "ACTOR", "TASK", "Actor", "Task", "invalid", "actors", "", " " })
    void shouldThrowExceptionForInvalidValues(String invalidValue) {
        assertThatThrownBy(() -> ResourceType.fromValue(invalidValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resource type");
    }

    /**
     * Tests that the fromValue() method throws an exception for null values.
     */
    @ParameterizedTest
    @NullSource
    void shouldThrowExceptionForNullValue(String nullValue) {
        assertThatThrownBy(() -> ResourceType.fromValue(nullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    // ==================== Enum Consistency Tests ====================

    /**
     * Tests that the enum has exactly two values.
     */
    @Test
    void shouldHaveExactlyTwoResourceTypes() {
        assertThat(ResourceType.values()).hasSize(2);
    }

    /**
     * Tests that the enum values can be round-tripped through the fromValue()
     * method.
     */
    @Test
    void shouldRoundTripThroughFromValue() {
        for (ResourceType type : ResourceType.values()) {
            assertThat(ResourceType.fromValue(type.getValue())).isEqualTo(type);
        }
    }
}
