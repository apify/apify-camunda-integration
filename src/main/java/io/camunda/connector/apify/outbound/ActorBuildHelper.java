package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts build-related information from Apify Actor API responses.
 */
final class ActorBuildHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorBuildHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ActorBuildHelper() {}

    /**
     * Extracts a build ID from an actor response for a specific build tag.
     *
     * @param actorResponse the JSON response from the get-actor API
     * @param buildTag      the tag to look up (e.g. "latest")
     * @return the build ID, or null if not found
     */
    static String extractBuildIdFromTag(String actorResponse, String buildTag) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(actorResponse);
            JsonNode dataNode = rootNode.path("data");
            JsonNode taggedBuildsNode = dataNode.isMissingNode()
                ? rootNode.path("taggedBuilds")
                : dataNode.path("taggedBuilds");

            if (taggedBuildsNode.isObject()) {
                JsonNode buildTagNode = taggedBuildsNode.path(buildTag);
                if (buildTagNode.isObject() && buildTagNode.has("buildId")) {
                    return buildTagNode.get("buildId").asText();
                }
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON response for build ID extraction: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts default (prefill) input values from a build response.
     *
     * @param buildResponse the JSON response from the get-build API
     * @return a map of property name to prefill value; never null
     */
    static Map<String, Object> extractDefaultInputFromBuild(String buildResponse) {
        Map<String, Object> defaultInput = new HashMap<>();

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(buildResponse);
            JsonNode dataNode = rootNode.path("data");
            JsonNode actorDefinitionNode = dataNode.isMissingNode()
                ? rootNode.path("actorDefinition")
                : dataNode.path("actorDefinition");

            if (actorDefinitionNode.isObject()) {
                JsonNode inputNode = actorDefinitionNode.path("input");
                if (inputNode.isObject()) {
                    JsonNode propertiesNode = inputNode.path("properties");
                    if (propertiesNode.isObject()) {
                        propertiesNode.properties().forEach(entry -> {
                            JsonNode propertyNode = entry.getValue();
                            if (propertyNode.isObject() && propertyNode.has("prefill")) {
                                defaultInput.put(entry.getKey(),
                                    OBJECT_MAPPER.convertValue(propertyNode.get("prefill"), Object.class));
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON response for default input extraction: {}", e.getMessage());
        }

        return defaultInput;
    }
}
