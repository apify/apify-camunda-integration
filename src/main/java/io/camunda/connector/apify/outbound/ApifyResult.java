package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Marker interface for all outbound connector operation results.
 * Provides a shared {@link ObjectMapper} instance for JSON processing in response DTOs.
 */
public interface ApifyResult {

    ObjectMapper SHARED_OBJECT_MAPPER = new ObjectMapper();
}
