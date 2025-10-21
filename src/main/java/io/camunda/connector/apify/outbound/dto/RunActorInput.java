package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;


public record RunActorInput(
    @NotEmpty String actorId,
    String inputJson,
    Integer timeout,
    String memory,
    String build,
    Boolean waitForFinish
) {}