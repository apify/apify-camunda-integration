package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.Valid;
import io.camunda.connector.generator.java.annotation.TemplateProperty;


public record ApifyRequestInput(
    @Valid @TemplateProperty(group = "apifyRequestInput") RunActorRequest runActorRequest,
    @Valid @TemplateProperty(group = "apifyRequestInput") RunTaskRequest runTaskRequest,
    @Valid @TemplateProperty(group = "apifyRequestInput") GetDatasetItemsRequest getDatasetItemsRequest,
    @Valid @TemplateProperty(group = "apifyRequestInput") ScrapeSingleUrlRequest scrapeSingleUrlRequest,
    @Valid @TemplateProperty(group = "apifyRequestInput") GetKeyValueStoreRecordRequest getKeyValueStoreRecordRequest
) {}
