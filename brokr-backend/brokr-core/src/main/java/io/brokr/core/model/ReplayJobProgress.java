package io.brokr.core.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress tracking for a replay job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayJobProgress {
    private Long messagesProcessed;  // Number of messages processed so far
    private Long messagesTotal;      // Total messages to process (if known)
    private Double throughput;       // Messages per second
    private Long estimatedTimeRemainingSeconds;  // Estimated time remaining in seconds
    private Map<String, Object> partitionProgress;  // Per-partition progress (partitionId -> progress)
}

