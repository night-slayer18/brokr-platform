package io.brokr.storage.entity;

import io.brokr.core.model.TopicMetrics;
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
@Table(name = "topic_metrics", indexes = {
    @Index(name = "idx_topic_metrics_cluster_timestamp", columnList = "cluster_id,timestamp DESC"),
    @Index(name = "idx_topic_metrics_topic_timestamp", columnList = "topic_name,timestamp DESC"),
    @Index(name = "idx_topic_metrics_cluster_topic_timestamp", columnList = "cluster_id,topic_name,timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMetricsEntity {
    @Id
    private String id;
    
    @Column(name = "cluster_id", nullable = false)
    private String clusterId;
    
    @Column(name = "topic_name", nullable = false)
    private String topicName;
    
    @Column(name = "messages_per_second_in")
    private Long messagesPerSecondIn;
    
    @Column(name = "bytes_per_second_in")
    private Long bytesPerSecondIn;
    
    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;
    
    @Column(name = "partition_count", nullable = false)
    private Integer partitionCount;
    
    @Column(name = "partition_sizes", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> partitionSizes;
    
    @Column(name = "partition_offsets", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> partitionOffsets;
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public TopicMetrics toDomain() {
        return TopicMetrics.builder()
                .id(id)
                .clusterId(clusterId)
                .topicName(topicName)
                .messagesPerSecondIn(messagesPerSecondIn)
                .bytesPerSecondIn(bytesPerSecondIn)
                .totalSizeBytes(totalSizeBytes)
                .partitionCount(partitionCount)
                .partitionSizes(partitionSizes)
                .partitionOffsets(partitionOffsets)
                .timestamp(timestamp)
                .build();
    }
    
    public static TopicMetricsEntity fromDomain(TopicMetrics metrics) {
        return TopicMetricsEntity.builder()
                .id(metrics.getId())
                .clusterId(metrics.getClusterId())
                .topicName(metrics.getTopicName())
                .messagesPerSecondIn(metrics.getMessagesPerSecondIn())
                .bytesPerSecondIn(metrics.getBytesPerSecondIn())
                .totalSizeBytes(metrics.getTotalSizeBytes())
                .partitionCount(metrics.getPartitionCount())
                .partitionSizes(metrics.getPartitionSizes())
                .partitionOffsets(metrics.getPartitionOffsets())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}

