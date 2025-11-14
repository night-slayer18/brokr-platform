package io.brokr.storage.entity;

import io.brokr.core.model.ClusterMetrics;
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
@Table(name = "cluster_metrics", indexes = {
    @Index(name = "idx_cluster_metrics_cluster_timestamp", columnList = "cluster_id,timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMetricsEntity {
    @Id
    private String id;
    
    @Column(name = "cluster_id", nullable = false)
    private String clusterId;
    
    @Column(name = "broker_count", nullable = false)
    private Integer brokerCount;
    
    @Column(name = "active_broker_count", nullable = false)
    private Integer activeBrokerCount;
    
    @Column(name = "total_topics", nullable = false)
    private Integer totalTopics;
    
    @Column(name = "total_partitions", nullable = false)
    private Integer totalPartitions;
    
    @Column(name = "total_messages_per_second")
    private Long totalMessagesPerSecond;
    
    @Column(name = "total_bytes_per_second")
    private Long totalBytesPerSecond;
    
    @Column(name = "is_healthy", nullable = false)
    private Boolean isHealthy;
    
    @Column(name = "connection_error_count")
    private Integer connectionErrorCount;
    
    @Column(name = "broker_details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> brokerDetails;
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public ClusterMetrics toDomain() {
        return ClusterMetrics.builder()
                .id(id)
                .clusterId(clusterId)
                .brokerCount(brokerCount)
                .activeBrokerCount(activeBrokerCount)
                .totalTopics(totalTopics)
                .totalPartitions(totalPartitions)
                .totalMessagesPerSecond(totalMessagesPerSecond)
                .totalBytesPerSecond(totalBytesPerSecond)
                .isHealthy(isHealthy)
                .connectionErrorCount(connectionErrorCount)
                .brokerDetails(brokerDetails)
                .timestamp(timestamp)
                .build();
    }
    
    public static ClusterMetricsEntity fromDomain(ClusterMetrics metrics) {
        return ClusterMetricsEntity.builder()
                .id(metrics.getId())
                .clusterId(metrics.getClusterId())
                .brokerCount(metrics.getBrokerCount())
                .activeBrokerCount(metrics.getActiveBrokerCount())
                .totalTopics(metrics.getTotalTopics())
                .totalPartitions(metrics.getTotalPartitions())
                .totalMessagesPerSecond(metrics.getTotalMessagesPerSecond())
                .totalBytesPerSecond(metrics.getTotalBytesPerSecond())
                .isHealthy(metrics.getIsHealthy())
                .connectionErrorCount(metrics.getConnectionErrorCount())
                .brokerDetails(metrics.getBrokerDetails())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}

