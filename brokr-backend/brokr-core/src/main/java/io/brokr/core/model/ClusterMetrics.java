package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMetrics {
    private String id;
    private String clusterId;
    
    // Broker metrics
    private Integer brokerCount;
    private Integer activeBrokerCount;
    
    // Topic metrics
    private Integer totalTopics;
    private Integer totalPartitions;
    
    // Throughput
    private Long totalMessagesPerSecond;
    private Long totalBytesPerSecond;
    
    // Health
    private Boolean isHealthy;
    private Integer connectionErrorCount;
    
    // Broker details
    private Map<String, Object> brokerDetails;
    
    private LocalDateTime timestamp;
}

