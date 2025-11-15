package io.brokr.api.input;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Input for creating a message replay job.
 */
@Data
public class MessageReplayInput {
    private String clusterId;
    private String sourceTopic;
    
    // Target configuration (either targetTopic or consumerGroupId, not both)
    private String targetTopic;  // For reprocessing to topic
    private String consumerGroupId;  // For offset reset
    
    // Starting point (either startOffset or startTimestamp, not both)
    private Long startOffset;
    private LocalDateTime startTimestamp;
    
    // Ending point (optional, for range replay)
    private Long endOffset;
    private LocalDateTime endTimestamp;
    
    // Partition selection (null = all partitions)
    private List<Integer> partitions;
    
    // Filter criteria (optional)
    private MessageFilterInput filters;
    
    // Transformation rules (optional)
    private MessageTransformationInput transformation;
    
    // Scheduling (optional)
    private LocalDateTime scheduleTime;  // For one-time scheduled jobs
    private String scheduleCron;  // For recurring jobs (e.g., "0 0 * * *")
    private String scheduleTimezone;  // Timezone for cron (default: UTC)
    
    // Retry configuration (optional)
    private Integer maxRetries;  // Maximum retry attempts (0 = no retry)
    private Integer retryDelaySeconds;  // Delay between retries in seconds (default: 60)
}

