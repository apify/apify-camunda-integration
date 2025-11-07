package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;


public record RunActorInput(
    @NotEmpty String actorId

    // TODO: add other fields for run actor
) {}