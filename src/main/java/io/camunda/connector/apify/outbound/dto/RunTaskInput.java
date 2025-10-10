package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;


public record RunTaskInput(
    @NotEmpty String taskId

    // TODO: add other fields for run task
) {}