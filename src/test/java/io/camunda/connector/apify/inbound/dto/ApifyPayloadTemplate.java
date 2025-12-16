package io.camunda.connector.apify.inbound.dto;

public final class ApifyPayloadTemplate {
    private static final String TEMPLATE_STRING = "{\n" +
            "    \"userId\": {{userId}},\n" +
            "    \"createdAt\": {{createdAt}},\n" +
            "    \"eventType\": {{eventType}},\n" +
            "    \"eventData\": {{eventData}},\n" +
            "    \"resource\": {{resource}}\n" +
            "}";

    private ApifyPayloadTemplate() {
    } // Prevent instantiation

    public static String getContent() {
        return TEMPLATE_STRING;
    }
}
