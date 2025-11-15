package io.brokr.core.model;

/**
 * Status of a message replay job.
 */
public enum ReplayJobStatus {
    PENDING,      // Job created but not started
    RUNNING,      // Job is currently executing
    COMPLETED,    // Job completed successfully
    FAILED,       // Job failed with error
    CANCELLED     // Job was cancelled by user
}

