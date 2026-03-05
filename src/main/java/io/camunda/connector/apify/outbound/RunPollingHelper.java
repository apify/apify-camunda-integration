package io.camunda.connector.apify.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.apify.common.ApifyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * Handles run status polling using Apify's server-side {@code waitForFinish} long-polling.
 */
final class RunPollingHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunPollingHelper.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FAILED", "ABORTED", "TIMED-OUT");

    private static final int MAX_POLL_DURATION_SECONDS = 300;
    private static final int SERVER_WAIT_FOR_FINISH_SECS = 30;

    private RunPollingHelper() {}

    /**
     * Polls the Apify API for a run's completion using server-side long-polling.
     *
     * @param apifyClient the client to use for API calls
     * @param runResponse the initial run response containing the run ID
     * @return the final status response when the run reaches a terminal state
     * @throws IOException if the run ID cannot be extracted or polling times out
     */
    static String pollRunStatus(ApifyClient apifyClient, String runResponse) throws IOException {
        String runId = extractRunId(runResponse);
        if (runId == null) {
            throw new IOException("Could not extract run ID from response");
        }

        long deadline = System.currentTimeMillis() + MAX_POLL_DURATION_SECONDS * 1000L;

        while (System.currentTimeMillis() < deadline) {
            int remainingSecs = (int) ((deadline - System.currentTimeMillis()) / 1000);
            int waitSecs = Math.min(SERVER_WAIT_FOR_FINISH_SECS, Math.max(remainingSecs, 1));

            String statusResponse = apifyClient.getRunStatus(runId, waitSecs).responseBody();

            if (extractTerminalStatus(statusResponse) != null) {
                return statusResponse;
            }
        }

        throw new IOException(
            "Polling timed out after " + MAX_POLL_DURATION_SECONDS + " seconds for run " + runId);
    }

    // visible for testing
    static String extractRunId(String response) {
        try {
            if (response == null || response.trim().isEmpty()) {
                return null;
            }

            JsonNode rootNode = OBJECT_MAPPER.readTree(response);

            JsonNode dataNode = rootNode.get("data");
            if (dataNode != null && dataNode.has("id")) {
                return dataNode.get("id").asText();
            }

            if (rootNode.has("id")) {
                return rootNode.get("id").asText();
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON response for run ID extraction: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns the terminal status string if the run has reached a terminal state, or {@code null}
     * if it is still running or the response cannot be parsed.
     */
    // visible for testing
    static String extractTerminalStatus(String statusResponse) {
        try {
            if (statusResponse == null || statusResponse.trim().isEmpty()) {
                return null;
            }

            JsonNode rootNode = OBJECT_MAPPER.readTree(statusResponse);
            JsonNode dataNode = rootNode.get("data");

            if (dataNode != null && dataNode.has("status")) {
                String status = dataNode.get("status").asText();
                return TERMINAL_STATUSES.contains(status) ? status : null;
            }

            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON response for status check: {}", e.getMessage());
            return null;
        }
    }
}
