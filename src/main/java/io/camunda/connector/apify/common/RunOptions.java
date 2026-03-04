package io.camunda.connector.apify.common;

/**
 * Options for running an actor or task.
 */
public record RunOptions(
    Integer timeout,
    String memory,
    String build,
    Integer waitForFinishSecs
) {}
