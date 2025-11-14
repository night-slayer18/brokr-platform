package io.brokr.kafka.service;

import io.brokr.core.model.ClusterMetrics;
import io.brokr.storage.entity.ClusterMetricsEntity;
import io.brokr.storage.repository.ClusterMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterMetricsService {
    
    private final ClusterMetricsRepository repository;
    
    /**
     * Save a single metric - for individual saves
     */
    @Transactional
    public ClusterMetrics saveMetrics(ClusterMetrics metrics) {
        ClusterMetricsEntity entity = ClusterMetricsEntity.fromDomain(metrics);
        ClusterMetricsEntity saved = repository.save(entity);
        return saved.toDomain();
    }
    
    /**
     * Batch save metrics - optimized to avoid N+1 queries
     */
    @Transactional
    public List<ClusterMetrics> saveAllMetrics(List<ClusterMetrics> metricsList) {
        if (metricsList == null || metricsList.isEmpty()) {
            return List.of();
        }
        
        List<ClusterMetricsEntity> entities = metricsList.stream()
                .map(ClusterMetricsEntity::fromDomain)
                .collect(Collectors.toList());
        
        // Batch insert - JPA will optimize this if batch size is configured
        List<ClusterMetricsEntity> saved = repository.saveAll(entities);
        
        log.debug("Saved {} cluster metrics in batch", saved.size());
        
        return saved.stream()
                .map(ClusterMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for a cluster within time range - single query
     */
    public List<ClusterMetrics> getMetrics(String clusterId,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ClusterMetricsEntity> entities = repository.findByClusterIdAndTimestampBetween(
                clusterId, startTime, endTime, pageable);
        
        return entities.stream()
                .map(ClusterMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for multiple clusters - batch query to avoid N+1
     */
    public List<ClusterMetrics> getMetricsForClusters(List<String> clusterIds,
                                                       LocalDateTime startTime, LocalDateTime endTime) {
        List<ClusterMetricsEntity> entities = repository.findByClusterIdsInAndTimestampBetween(
                clusterIds, startTime, endTime);
        
        return entities.stream()
                .map(ClusterMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get latest metrics for a cluster
     */
    public ClusterMetrics getLatestMetrics(String clusterId) {
        Pageable pageable = PageRequest.of(0, 1);
        List<ClusterMetricsEntity> entities = repository.findLatestByClusterId(clusterId, pageable);
        
        if (entities.isEmpty()) {
            return null;
        }
        
        return entities.get(0).toDomain();
    }
    
    /**
     * Get aggregated metrics - single query with aggregation
     */
    public Object[] getAggregatedMetrics(String clusterId,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        return repository.getAggregatedMetrics(clusterId, startTime, endTime);
    }
    
    /**
     * Delete old metrics for retention policy - batch delete
     */
    @Transactional
    public void deleteMetricsBefore(LocalDateTime cutoff) {
        repository.deleteByTimestampBefore(cutoff);
        log.info("Deleted cluster metrics older than {}", cutoff);
    }
}

