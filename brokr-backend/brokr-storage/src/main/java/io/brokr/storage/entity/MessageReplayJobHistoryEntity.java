package io.brokr.storage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "message_replay_job_history", indexes = {
    @Index(name = "idx_replay_job_history_job_timestamp", columnList = "replay_job_id,timestamp DESC"),
    @Index(name = "idx_replay_job_history_timestamp", columnList = "timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplayJobHistoryEntity {
    @Id
    private String id;
    
    @Column(name = "replay_job_id", nullable = false)
    private String replayJobId;
    
    @Column(name = "action", nullable = false, length = 50)
    private String action;  // ACTION_STARTED, MESSAGE_PROCESSED, ACTION_COMPLETED, ACTION_FAILED
    
    @Column(name = "message_count")
    @Builder.Default
    private Long messageCount = 0L;
    
    @Column(name = "throughput", precision = 10, scale = 2)
    private BigDecimal throughput;  // Messages per second
    
    @Column(name = "timestamp", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;
    
    public static MessageReplayJobHistoryEntity create(String replayJobId, String action, Long messageCount, Double throughput, Map<String, Object> details) {
        return MessageReplayJobHistoryEntity.builder()
                .id(UUID.randomUUID().toString())
                .replayJobId(replayJobId)
                .action(action)
                .messageCount(messageCount)
                .throughput(throughput != null ? BigDecimal.valueOf(throughput) : null)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();
    }
}

