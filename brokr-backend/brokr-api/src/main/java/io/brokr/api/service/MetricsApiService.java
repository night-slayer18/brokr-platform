package io.brokr.api.service;

import io.brokr.core.model.*;
import io.brokr.kafka.service.*;
import io.brokr.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsApiService {
    
    private final TopicMetricsService topicMetricsService;
    private final ConsumerGroupMetricsService consumerGroupMetricsService;
    private final ClusterMetricsService clusterMetricsService;
    private final AuthorizationService authorizationService;
    
    /**
     * Get topic metrics for a time range
     * Uses batch query to avoid N+1
     * Cached for frequently accessed recent metrics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "topicMetrics", key = "#clusterId + ':' + #topicName + ':' + #startTime + ':' + #endTime + ':' + #limit", 
               unless = "#result == null || #result.isEmpty()")
    public List<TopicMetrics> getTopicMetrics(String clusterId, String topicName, 
                                              long startTime, long endTime, int limit) {
        // Verify access to cluster
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());
        
        return topicMetricsService.getMetrics(clusterId, topicName, start, end, limit);
    }
    
    /**
     * Get consumer group metrics for a time range
     * Uses batch query to avoid N+1
     * Cached for frequently accessed recent metrics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "consumerGroupMetrics", key = "#clusterId + ':' + #consumerGroupId + ':' + #startTime + ':' + #endTime + ':' + #limit",
               unless = "#result == null || #result.isEmpty()")
    public List<ConsumerGroupMetrics> getConsumerGroupMetrics(String clusterId, String consumerGroupId,
                                                               long startTime, long endTime, int limit) {
        // Verify access to cluster
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());
        
        return consumerGroupMetricsService.getMetrics(clusterId, consumerGroupId, start, end, limit);
    }
    
    /**
     * Get cluster metrics for a time range
     * Uses batch query to avoid N+1
     * Cached for frequently accessed recent metrics
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "clusterMetrics", key = "#clusterId + ':' + #startTime + ':' + #endTime + ':' + #limit",
               unless = "#result == null || #result.isEmpty()")
    public List<ClusterMetrics> getClusterMetrics(String clusterId,
                                                   long startTime, long endTime, int limit) {
        // Verify access to cluster
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());
        
        return clusterMetricsService.getMetrics(clusterId, start, end, limit);
    }
}

