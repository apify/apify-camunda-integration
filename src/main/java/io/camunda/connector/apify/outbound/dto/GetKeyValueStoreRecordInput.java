package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;

public record GetKeyValueStoreRecordInput(
    @NotEmpty String storeId,
    @NotEmpty String recordKey
) {}

