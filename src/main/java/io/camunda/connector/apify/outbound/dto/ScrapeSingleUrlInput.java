package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record ScrapeSingleUrlInput(
    @NotEmpty
    @Pattern(regexp = "^https?://.*$", message = "URL must start with http:// or https://")
    String url,

    @NotEmpty
    @Pattern(regexp = "^(cheerio|playwright:adaptive|playwright:firefox)$", message = "Crawler type must be one of: cheerio, playwright:adaptive, playwright:firefox")
    String crawlerType
) {}
