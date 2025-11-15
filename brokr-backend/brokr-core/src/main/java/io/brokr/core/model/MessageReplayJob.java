package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Domain model for message replay/reprocessing jobs.
 * Represents a job that replays messages from a source topic to a target (consumer group or topic).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplayJob {
    private String id;
    private String clusterId;
    private String sourceTopic;
    
    // Target configuration
    private String targetTopic;  // NULL for offset reset only
    private String consumerGroupId;  // NULL for reprocessing to topic
    
    // Starting point (either offset or timestamp, not both)
    private Long startOffset;  // NULL if using timestamp
    private LocalDateTime startTimestamp;  // NULL if using offset
    
    // Ending point (optional, for range replay)
    private Long endOffset;
    private LocalDateTime endTimestamp;
    
    // Partition selection (list of partition IDs, NULL = all partitions)
    private List<Integer> partitions;
    
    // Filter criteria
    private MessageFilter filters;
    
    // Transformation rules (optional)
    private MessageTransformation transformation;
    
    // Job status and progress
    private ReplayJobStatus status;
    private ReplayJobProgress progress;
    
    // Scheduling (for recurring jobs)
    private String scheduleCron;  // Cron expression (e.g., "0 0 * * *" for daily)
    private String scheduleTimezone;  // Timezone for cron (default: UTC)
    private LocalDateTime nextScheduledRun;  // Next scheduled execution time
    private Boolean isRecurring;  // Whether this is a recurring job
    private LocalDateTime lastScheduledRun;  // Last scheduled execution time
    
    // Retry configuration
    private Integer retryCount;  // Current retry attempt
    private Integer maxRetries;  // Maximum retry attempts (0 = no retry)
    private Integer retryDelaySeconds;  // Delay between retries in seconds
    
    // Metadata
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Map<String, Object> metadata;  // Additional job metadata
}

