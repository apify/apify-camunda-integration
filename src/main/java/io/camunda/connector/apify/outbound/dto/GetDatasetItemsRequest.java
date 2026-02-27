package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

public record GetDatasetItemsRequest(
    @NotEmpty String datasetId,
    @Min(value = 0, message = "offset can't be negative") Integer offset,
    @Min(value = 0, message = "limit can't be negative") Integer limit
) {}
