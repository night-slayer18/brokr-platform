package io.brokr.storage.entity;

import io.brokr.core.model.MessageReplayJob;
import io.brokr.core.model.ReplayJobStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "message_replay_jobs", indexes = {
    @Index(name = "idx_replay_jobs_cluster_status_created", columnList = "cluster_id,status,created_at DESC"),
    @Index(name = "idx_replay_jobs_created_by_created", columnList = "created_by,created_at DESC"),
    @Index(name = "idx_replay_jobs_source_topic_created", columnList = "source_topic,created_at DESC"),
    @Index(name = "idx_replay_jobs_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplayJobEntity {
    @Id
    private String id;
    
    @Column(name = "cluster_id", nullable = false)
    private String clusterId;
    
    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;
    
    @Column(name = "target_topic")
    private String targetTopic;
    
    @Column(name = "consumer_group_id")
    private String consumerGroupId;
    
    @Column(name = "start_offset")
    private Long startOffset;
    
    @Column(name = "start_timestamp")
    private LocalDateTime startTimestamp;
    
    @Column(name = "end_offset")
    private Long endOffset;
    
    @Column(name = "end_timestamp")
    private LocalDateTime endTimestamp;
    
    @Column(name = "partitions", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> partitions;
    
    @Column(name = "filters", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> filtersJson;
    
    @Column(name = "transformation", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> transformationJson;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReplayJobStatus status = ReplayJobStatus.PENDING;
    
    @Column(name = "progress", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> progressJson;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    // Scheduling fields
    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;
    
    @Column(name = "schedule_timezone", length = 50)
    @Builder.Default
    private String scheduleTimezone = "UTC";
    
    @Column(name = "next_scheduled_run")
    private LocalDateTime nextScheduledRun;
    
    @Column(name = "is_recurring")
    @Builder.Default
    private Boolean isRecurring = false;
    
    @Column(name = "last_scheduled_run")
    private LocalDateTime lastScheduledRun;
    
    // Retry fields
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 0;
    
    @Column(name = "retry_delay_seconds")
    @Builder.Default
    private Integer retryDelaySeconds = 60;
    
    // Helper methods to convert between domain and entity
    // Note: Full conversion of filters/transformation requires JSON serialization
    // For now, we'll use Map<String, Object> and handle conversion in service layer
    
    public MessageReplayJob toDomain() {
        return MessageReplayJob.builder()
                .id(id)
                .clusterId(clusterId)
                .sourceTopic(sourceTopic)
                .targetTopic(targetTopic)
                .consumerGroupId(consumerGroupId)
                .startOffset(startOffset)
                .startTimestamp(startTimestamp)
                .endOffset(endOffset)
                .endTimestamp(endTimestamp)
                .partitions(partitions)
                // Filters and transformation will be deserialized in service layer
                .status(status)
                // Progress will be deserialized in service layer
                .createdBy(createdBy)
                .createdAt(createdAt)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .errorMessage(errorMessage)
                .scheduleCron(scheduleCron)
                .scheduleTimezone(scheduleTimezone)
                .nextScheduledRun(nextScheduledRun)
                .isRecurring(isRecurring)
                .lastScheduledRun(lastScheduledRun)
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .retryDelaySeconds(retryDelaySeconds)
                .metadata(metadata)
                .build();
    }
    
    public static MessageReplayJobEntity fromDomain(MessageReplayJob job) {
        return MessageReplayJobEntity.builder()
                .id(job.getId() != null ? job.getId() : UUID.randomUUID().toString())
                .clusterId(job.getClusterId())
                .sourceTopic(job.getSourceTopic())
                .targetTopic(job.getTargetTopic())
                .consumerGroupId(job.getConsumerGroupId())
                .startOffset(job.getStartOffset())
                .startTimestamp(job.getStartTimestamp())
                .endOffset(job.getEndOffset())
                .endTimestamp(job.getEndTimestamp())
                .partitions(job.getPartitions())
                // Filters and transformation will be serialized in service layer
                .status(job.getStatus() != null ? job.getStatus() : ReplayJobStatus.PENDING)
                // Progress will be serialized in service layer
                .createdBy(job.getCreatedBy())
                .createdAt(job.getCreatedAt() != null ? job.getCreatedAt() : LocalDateTime.now())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .scheduleCron(job.getScheduleCron())
                .scheduleTimezone(job.getScheduleTimezone() != null ? job.getScheduleTimezone() : "UTC")
                .nextScheduledRun(job.getNextScheduledRun())
                .isRecurring(job.getIsRecurring() != null ? job.getIsRecurring() : false)
                .lastScheduledRun(job.getLastScheduledRun())
                .retryCount(job.getRetryCount() != null ? job.getRetryCount() : 0)
                .maxRetries(job.getMaxRetries() != null ? job.getMaxRetries() : 0)
                .retryDelaySeconds(job.getRetryDelaySeconds() != null ? job.getRetryDelaySeconds() : 60)
                .metadata(job.getMetadata())
                .build();
    }
}

