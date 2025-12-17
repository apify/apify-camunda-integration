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

    @Nested
    @DisplayName("getValue()")
    class GetValue {

        @Test
        void shouldReturnCorrectValueForActor() {
            assertThat(ResourceType.ACTOR.getValue()).isEqualTo("actor");
        }

        @Test
        void shouldReturnCorrectValueForTask() {
            assertThat(ResourceType.TASK.getValue()).isEqualTo("task");
        }
    }

    @Nested
    @DisplayName("getConditionKey()")
    class GetConditionKey {

        @Test
        void shouldReturnCorrectConditionKeyForActor() {
            assertThat(ResourceType.ACTOR.getConditionKey()).isEqualTo("actorId");
        }

        @Test
        void shouldReturnCorrectConditionKeyForTask() {
            assertThat(ResourceType.TASK.getConditionKey()).isEqualTo("actorTaskId");
        }
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
            ResourceType result = ResourceType.fromValue(value);

            assertThat(result).isEqualTo(expected);
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

        @Test
        void shouldHaveUniqueValues() {
            assertThat(ResourceType.ACTOR.getValue()).isNotEqualTo(ResourceType.TASK.getValue());
        }

        @Test
        void shouldHaveUniqueConditionKeys() {
            assertThat(ResourceType.ACTOR.getConditionKey()).isNotEqualTo(ResourceType.TASK.getConditionKey());
        }
    }
}
