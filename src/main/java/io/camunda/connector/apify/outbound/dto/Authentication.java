package io.camunda.connector.apify.outbound.dto;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotEmpty;

public record Authentication(
    @NotEmpty @TemplateProperty(group = "authentication", description = "The token for authentication") String token

    // TODO: OAuth2, use the below for the connector template
    // {
    //     "id": "authentication.clientId",
    //     "label": "Client ID",
    //     "optional": false,
    //     "constraints": { "notEmpty": true },
    //     "group": "authentication",
    //     "binding": { "name": "authentication.clientId", "type": "zeebe:input" },
    //     "condition": {
    //       "property": "authentication.method",
    //       "equals": "oauth",
    //       "type": "simple"
    //     },
    //     "type": "String"
    //   },
    //   {
    //     "id": "authentication.clientSecret",
    //     "label": "Client Secret",
    //     "optional": false,
    //     "constraints": { "notEmpty": true },
    //     "group": "authentication",
    //     "binding": {
    //       "name": "authentication.clientSecret",
    //       "type": "zeebe:input"
    //     },
    //     "condition": {
    //       "property": "authentication.method",
    //       "equals": "oauth",
    //       "type": "simple"
    //     },
    //     "type": "String"
    //   },
) {}
