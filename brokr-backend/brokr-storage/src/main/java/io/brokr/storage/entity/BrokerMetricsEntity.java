package io.brokr.storage.entity;

import io.brokr.core.model.BrokerMetrics;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for storing broker-level metrics.
 * Indexed for efficient time-range queries by cluster and broker.
 */
@Entity
@Table(name = "broker_metrics", indexes = {
    @Index(name = "idx_broker_metrics_cluster_broker_ts", columnList = "cluster_id,broker_id,timestamp DESC"),
    @Index(name = "idx_broker_metrics_cluster_ts", columnList = "cluster_id,timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerMetricsEntity {
    @Id
    private String id;
    
    @Column(name = "cluster_id", nullable = false)
    private String clusterId;
    
    @Column(name = "broker_id", nullable = false)
    private int brokerId;
    
    // Resource metrics (from JMX)
    @Column(name = "cpu_usage_percent")
    private Double cpuUsagePercent;
    
    @Column(name = "memory_used_bytes")
    private Long memoryUsedBytes;
    
    @Column(name = "memory_max_bytes")
    private Long memoryMaxBytes;
    
    @Column(name = "disk_used_bytes")
    private Long diskUsedBytes;
    
    @Column(name = "disk_total_bytes")
    private Long diskTotalBytes;
    
    // Throughput metrics (from JMX)
    @Column(name = "bytes_in_per_second")
    private Long bytesInPerSecond;
    
    @Column(name = "bytes_out_per_second")
    private Long bytesOutPerSecond;
    
    @Column(name = "messages_in_per_second")
    private Long messagesInPerSecond;
    
    @Column(name = "requests_per_second")
    private Long requestsPerSecond;
    
    // Partition metrics (from Admin API)
    @Column(name = "leader_partition_count")
    private Integer leaderPartitionCount;
    
    @Column(name = "replica_partition_count")
    private Integer replicaPartitionCount;
    
    @Column(name = "under_replicated_partitions")
    private Integer underReplicatedPartitions;
    
    @Column(name = "offline_partitions")
    private Integer offlinePartitions;
    
    // Health status
    @Column(name = "is_controller")
    private Boolean isController;
    
    @Column(name = "is_healthy")
    private Boolean isHealthy;
    
    @Column(name = "last_error")
    private String lastError;
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public BrokerMetrics toDomain() {
        return BrokerMetrics.builder()
                .id(id)
                .clusterId(clusterId)
                .brokerId(brokerId)
                .cpuUsagePercent(cpuUsagePercent)
                .memoryUsedBytes(memoryUsedBytes)
                .memoryMaxBytes(memoryMaxBytes)
                .diskUsedBytes(diskUsedBytes)
                .diskTotalBytes(diskTotalBytes)
                .bytesInPerSecond(bytesInPerSecond)
                .bytesOutPerSecond(bytesOutPerSecond)
                .messagesInPerSecond(messagesInPerSecond)
                .requestsPerSecond(requestsPerSecond)
                .leaderPartitionCount(leaderPartitionCount)
                .replicaPartitionCount(replicaPartitionCount)
                .underReplicatedPartitions(underReplicatedPartitions)
                .offlinePartitions(offlinePartitions)
                .isController(isController)
                .isHealthy(isHealthy)
                .lastError(lastError)
                .timestamp(timestamp)
                .build();
    }
    
    public static BrokerMetricsEntity fromDomain(BrokerMetrics metrics) {
        return BrokerMetricsEntity.builder()
                .id(metrics.getId())
                .clusterId(metrics.getClusterId())
                .brokerId(metrics.getBrokerId())
                .cpuUsagePercent(metrics.getCpuUsagePercent())
                .memoryUsedBytes(metrics.getMemoryUsedBytes())
                .memoryMaxBytes(metrics.getMemoryMaxBytes())
                .diskUsedBytes(metrics.getDiskUsedBytes())
                .diskTotalBytes(metrics.getDiskTotalBytes())
                .bytesInPerSecond(metrics.getBytesInPerSecond())
                .bytesOutPerSecond(metrics.getBytesOutPerSecond())
                .messagesInPerSecond(metrics.getMessagesInPerSecond())
                .requestsPerSecond(metrics.getRequestsPerSecond())
                .leaderPartitionCount(metrics.getLeaderPartitionCount())
                .replicaPartitionCount(metrics.getReplicaPartitionCount())
                .underReplicatedPartitions(metrics.getUnderReplicatedPartitions())
                .offlinePartitions(metrics.getOfflinePartitions())
                .isController(metrics.getIsController())
                .isHealthy(metrics.getIsHealthy())
                .lastError(metrics.getLastError())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}
