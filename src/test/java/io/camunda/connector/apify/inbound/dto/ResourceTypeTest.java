package io.camunda.connector.apify.inbound.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for ResourceType enum.
 */
class ResourceTypeTest {

    @ParameterizedTest
    @CsvSource({
            "ACTOR, actor, actorId",
            "TASK, task, actorTaskId"
    })
    @DisplayName("Should return correct value and conditionKey for each type")
    void shouldReturnCorrectValueAndConditionKey(ResourceType type, String expectedValue, String expectedConditionKey) {
        assertThat(type.getValue()).isEqualTo(expectedValue);
        assertThat(type.getConditionKey()).isEqualTo(expectedConditionKey);
    }

    @Nested
    @DisplayName("fromValue()")
    class FromValue {

        @ParameterizedTest
        @CsvSource({
                "actor, ACTOR",
                "task, TASK"
        })
        void shouldParseValidResourceTypeValues(String value, ResourceType expected) {
            assertThat(ResourceType.fromValue(value)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ACTOR", "TASK", "Actor", "Task", "invalid", "actors", "", " "})
        void shouldThrowExceptionForInvalidValues(String invalidValue) {
            assertThatThrownBy(() -> ResourceType.fromValue(invalidValue))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid resource type");
        }

        @ParameterizedTest
        @NullSource
        void shouldThrowExceptionForNullValue(String nullValue) {
            assertThatThrownBy(() -> ResourceType.fromValue(nullValue))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null");
        }
    }

    @Nested
    @DisplayName("Enum Consistency")
    class EnumConsistency {

        @Test
        void shouldHaveExactlyTwoResourceTypes() {
            assertThat(ResourceType.values()).hasSize(2);
        }

        @Test
        void shouldRoundTripThroughFromValue() {
            for (ResourceType type : ResourceType.values()) {
                assertThat(ResourceType.fromValue(type.getValue())).isEqualTo(type);
            }
        }
    }
}
