package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;

public record GetDatasetItemsInput(
    @NotEmpty String datasetId,
    Integer offset,
    Integer limit
) {}
