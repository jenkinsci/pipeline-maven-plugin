package org.jenkinsci.plugins.pipeline.maven.model;

/**
 * See {@code org.apache.maven.execution.ExecutionEvent.Type}
 */
public enum MavenExecutionEventType {
    ProjectDiscoveryStarted,
    SessionStarted,
    SessionEnded,
    ProjectSkipped,
    ProjectStarted,
    ProjectSucceeded,
    ProjectFailed,
    MojoSkipped,
    MojoStarted,
    MojoSucceeded,
    MojoFailed,
    ForkStarted,
    ForkSucceeded,
    ForkFailed,
    ForkedProjectStarted,
    ForkedProjectSucceeded,
    ForkedProjectFailed
}
