package io.camunda.connector.apify.outbound.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;


public record ScrapeSingleUrlRequest(
    @NotEmpty
    @Pattern(regexp = "^https?://.*$", message = "URL must start with http:// or https://")
    String url,

    @Pattern(regexp = "^(cheerio|jsdom|playwright:adaptive|playwright:firefox)$", message = "Crawler type must be one of: cheerio, jsdom, playwright:adaptive, playwright:firefox")
    String crawlerType
) {}
