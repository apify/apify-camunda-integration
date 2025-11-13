package io.camunda.connector.apify.outbound.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;


public record RunActorInput(
    @NotEmpty String actorId,
    JsonNode inputJson,
    Integer timeout,
    String memory,
    String build,
    Boolean waitForFinish
) {}