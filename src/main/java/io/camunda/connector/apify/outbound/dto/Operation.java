package io.camunda.connector.apify.outbound.dto;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;


public record Operation(
    @NotEmpty @TemplateProperty(group = "operation") String type
) {}