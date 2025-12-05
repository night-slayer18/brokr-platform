package io.brokr.kafka.service;

import io.brokr.core.model.BrokerMetrics;
import io.brokr.storage.entity.BrokerMetricsEntity;
import io.brokr.storage.repository.BrokerMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing broker metrics storage and retrieval.
 * Uses batch operations to prevent N+1 query issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrokerMetricsService {
    
    private final BrokerMetricsRepository repository;
    
    /**
     * Save a single broker metric.
     */
    @Transactional
    public BrokerMetrics saveMetrics(BrokerMetrics metrics) {
        BrokerMetricsEntity entity = BrokerMetricsEntity.fromDomain(metrics);
        BrokerMetricsEntity saved = repository.save(entity);
        return saved.toDomain();
    }
    
    /**
     * Batch save metrics - optimized to avoid N+1 queries.
     * Uses JPA batch insert if configured.
     */
    @Transactional
    public List<BrokerMetrics> saveAllMetrics(List<BrokerMetrics> metricsList) {
        if (metricsList == null || metricsList.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<BrokerMetricsEntity> entities = metricsList.stream()
                .map(BrokerMetricsEntity::fromDomain)
                .collect(Collectors.toList());
        
        List<BrokerMetricsEntity> saved = repository.saveAll(entities);
        
        log.debug("Saved {} broker metrics in batch", saved.size());
        
        return saved.stream()
                .map(BrokerMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for all brokers in a cluster within time range.
     * Single query - no N+1 issues.
     */
    public List<BrokerMetrics> getMetrics(String clusterId, LocalDateTime startTime, 
                                           LocalDateTime endTime, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<BrokerMetricsEntity> entities = repository.findByClusterIdAndTimestampBetween(
                clusterId, startTime, endTime, pageable);
        
        return entities.stream()
                .map(BrokerMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for a specific broker within time range.
     */
    public List<BrokerMetrics> getMetricsByBroker(String clusterId, int brokerId,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<BrokerMetricsEntity> entities = repository.findByClusterIdAndBrokerIdAndTimestampBetween(
                clusterId, brokerId, startTime, endTime, pageable);
        
        return entities.stream()
                .map(BrokerMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get latest metrics for all brokers in a cluster.
     * Uses efficient subquery - single query for all brokers.
     */
    public List<BrokerMetrics> getLatestMetrics(String clusterId) {
        List<BrokerMetricsEntity> entities = repository.findLatestByClusterId(clusterId);
        
        return entities.stream()
                .map(BrokerMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get latest metrics for a specific broker.
     */
    public BrokerMetrics getLatestMetricsByBroker(String clusterId, int brokerId) {
        Pageable pageable = PageRequest.of(0, 1);
        List<BrokerMetricsEntity> entities = repository.findLatestByClusterIdAndBrokerId(
                clusterId, brokerId, pageable);
        
        if (entities.isEmpty()) {
            return null;
        }
        
        return entities.get(0).toDomain();
    }
    
    /**
     * Get metrics for multiple clusters - batch query to avoid N+1.
     */
    public List<BrokerMetrics> getMetricsForClusters(List<String> clusterIds,
                                                      LocalDateTime startTime, LocalDateTime endTime) {
        List<BrokerMetricsEntity> entities = repository.findByClusterIdsInAndTimestampBetween(
                clusterIds, startTime, endTime);
        
        return entities.stream()
                .map(BrokerMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Delete old metrics for retention policy - batch delete.
     */
    @Transactional
    public void deleteMetricsBefore(LocalDateTime cutoff) {
        repository.deleteByTimestampBefore(cutoff);
        log.info("Deleted broker metrics older than {}", cutoff);
    }
    
    /**
     * Get count of metrics for a cluster - for monitoring.
     */
    public long getMetricsCount(String clusterId) {
        return repository.countByClusterId(clusterId);
    }
}
