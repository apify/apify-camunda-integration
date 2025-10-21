package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;


public record RunTaskInput(
    @NotEmpty String taskId,
    String inputJson,
    Integer timeout,
    String memory,
    String build,
    Boolean waitForFinish
) {}