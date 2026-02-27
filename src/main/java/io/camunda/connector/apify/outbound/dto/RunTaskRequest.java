package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;

public record RunTaskRequest(
    @NotEmpty String taskId,
    JsonNode inputJson,
    @Min(value = 0, message = "timeout can't be negative") Integer timeout,
    String memory,
    String buildTag,
    Boolean waitForFinish
) {}
