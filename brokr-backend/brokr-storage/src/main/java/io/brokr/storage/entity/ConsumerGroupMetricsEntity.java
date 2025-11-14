package io.brokr.storage.entity;

import io.brokr.core.model.ConsumerGroupMetrics;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "consumer_group_metrics", indexes = {
    @Index(name = "idx_consumer_group_metrics_cluster_timestamp", columnList = "cluster_id,timestamp DESC"),
    @Index(name = "idx_consumer_group_metrics_group_timestamp", columnList = "consumer_group_id,timestamp DESC"),
    @Index(name = "idx_consumer_group_metrics_cluster_group_timestamp", columnList = "cluster_id,consumer_group_id,timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerGroupMetricsEntity {
    @Id
    private String id;
    
    @Column(name = "cluster_id", nullable = false)
    private String clusterId;
    
    @Column(name = "consumer_group_id", nullable = false)
    private String consumerGroupId;
    
    @Column(name = "total_lag")
    private Long totalLag;
    
    @Column(name = "max_lag")
    private Long maxLag;
    
    @Column(name = "min_lag")
    private Long minLag;
    
    @Column(name = "avg_lag")
    private Long avgLag;
    
    @Column(name = "total_offset")
    private Long totalOffset;
    
    @Column(name = "committed_offset")
    private Long committedOffset;
    
    @Column(name = "member_count")
    private Integer memberCount;
    
    @Column(name = "active_member_count")
    private Integer activeMemberCount;
    
    @Column(name = "topic_lags", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Long> topicLags;
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public ConsumerGroupMetrics toDomain() {
        return ConsumerGroupMetrics.builder()
                .id(id)
                .clusterId(clusterId)
                .consumerGroupId(consumerGroupId)
                .totalLag(totalLag)
                .maxLag(maxLag)
                .minLag(minLag)
                .avgLag(avgLag)
                .totalOffset(totalOffset)
                .committedOffset(committedOffset)
                .memberCount(memberCount)
                .activeMemberCount(activeMemberCount)
                .topicLags(topicLags)
                .timestamp(timestamp)
                .build();
    }
    
    public static ConsumerGroupMetricsEntity fromDomain(ConsumerGroupMetrics metrics) {
        return ConsumerGroupMetricsEntity.builder()
                .id(metrics.getId())
                .clusterId(metrics.getClusterId())
                .consumerGroupId(metrics.getConsumerGroupId())
                .totalLag(metrics.getTotalLag())
                .maxLag(metrics.getMaxLag())
                .minLag(metrics.getMinLag())
                .avgLag(metrics.getAvgLag())
                .totalOffset(metrics.getTotalOffset())
                .committedOffset(metrics.getCommittedOffset())
                .memberCount(metrics.getMemberCount())
                .activeMemberCount(metrics.getActiveMemberCount())
                .topicLags(metrics.getTopicLags())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}

