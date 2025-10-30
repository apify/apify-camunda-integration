package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;

public record ScrapeSingleUrlInput(
    @NotEmpty String url,
    @NotEmpty String crawlerType
) {}
