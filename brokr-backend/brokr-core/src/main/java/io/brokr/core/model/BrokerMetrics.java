package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Domain model for broker-level metrics.
 * Captures both JMX-based resource metrics and Admin API partition metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerMetrics {
    private String id;
    private String clusterId;
    private int brokerId;
    
    // Resource metrics (from JMX - null if JMX not available)
    private Double cpuUsagePercent;
    private Long memoryUsedBytes;
    private Long memoryMaxBytes;
    private Long diskUsedBytes;
    private Long diskTotalBytes;
    
    // Throughput metrics (from JMX)
    private Long bytesInPerSecond;
    private Long bytesOutPerSecond;
    private Long messagesInPerSecond;
    private Long requestsPerSecond;
    
    // Partition metrics (from Admin API - always available)
    private Integer leaderPartitionCount;
    private Integer replicaPartitionCount;
    private Integer underReplicatedPartitions;
    private Integer offlinePartitions;
    
    // Health status
    private Boolean isController;
    private Boolean isHealthy;
    private String lastError;
    
    private LocalDateTime timestamp;
}
