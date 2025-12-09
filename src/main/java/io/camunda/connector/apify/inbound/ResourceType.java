package io.camunda.connector.apify.inbound;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The type of Apify resource to subscribe to for webhook events.
 */
public enum ResourceType {
    ACTOR("actor", "actorId"),
    TASK("task", "actorTaskId");

    private final String value;
    private final String conditionKey;

    ResourceType(String value, String conditionKey) {
        this.value = value;
        this.conditionKey = conditionKey;
    }

    /**
     * The JSON value used for serialization/deserialization.
     * This matches the values used in the element template dropdown.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * The key used in the Apify webhook condition object.
     * For actors: "actorId", for tasks: "actorTaskId".
     */
    public String getConditionKey() {
        return conditionKey;
    }
}

