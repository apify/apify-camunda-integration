package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.Valid;
import io.camunda.connector.generator.java.annotation.TemplateProperty;


public record ApifyRequestInput(
    @Valid @TemplateProperty(group = "apifyRequestInput") RunActorInput runActorInput,
    @Valid @TemplateProperty(group = "apifyRequestInput") RunTaskInput runTaskInput,
    @Valid @TemplateProperty(group = "apifyRequestInput") GetDatasetItemsInput getDatasetItemsInput,
    @Valid @TemplateProperty(group = "apifyRequestInput") ScrapeSingleUrlInput scrapeSingleUrlInput
) {}