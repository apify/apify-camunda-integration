package io.camunda.connector.apify.common;

/**
 * Options for running an actor or task.
 */
public class RunOptions {
    final Integer timeout;
    final String memory;
    final String build;
    final Integer waitForFinishSecs;
    
    public RunOptions(Integer timeout, String memory, String build, Integer waitForFinishSecs) {
        this.timeout = timeout;
        this.memory = memory;
        this.build = build;
        this.waitForFinishSecs = waitForFinishSecs;
    }
}

