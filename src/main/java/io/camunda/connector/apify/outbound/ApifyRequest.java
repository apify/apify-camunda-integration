package io.camunda.connector.apify.outbound;

import io.camunda.connector.apify.outbound.dto.Authentication;
import io.camunda.connector.apify.outbound.dto.Operation;
import io.camunda.connector.apify.outbound.dto.ApifyRequestInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


public record ApifyRequest(
    @Valid @NotNull Authentication authentication,
    @Valid @NotNull Operation operation,
    @Valid @NotNull ApifyRequestInput apifyRequestInput
) {}
