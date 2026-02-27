package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;

public record GetKeyValueStoreRecordRequest(
    @NotEmpty String storeId,
    @NotEmpty String recordKey
) {}
