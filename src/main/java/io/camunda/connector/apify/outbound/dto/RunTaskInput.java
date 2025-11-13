package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;


public record RunTaskInput(
    @NotEmpty String taskId,
    JsonNode inputJson,
    Integer timeout,
    String memory,
    String build,
    Boolean waitForFinish
) {}