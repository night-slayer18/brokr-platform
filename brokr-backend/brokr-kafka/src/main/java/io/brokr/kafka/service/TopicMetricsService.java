package io.brokr.kafka.service;

import io.brokr.core.model.TopicMetrics;
import io.brokr.storage.entity.TopicMetricsEntity;
import io.brokr.storage.repository.TopicMetricsRepository;
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
public class TopicMetricsService {
    
    private final TopicMetricsRepository repository;
    
    /**
     * Save a single metric - for individual saves
     */
    @Transactional
    public TopicMetrics saveMetrics(TopicMetrics metrics) {
        TopicMetricsEntity entity = TopicMetricsEntity.fromDomain(metrics);
        TopicMetricsEntity saved = repository.save(entity);
        return saved.toDomain();
    }
    
    /**
     * Batch save metrics - optimized to avoid N+1 queries
     * Uses JPA batch insert when configured
     */
    @Transactional
    public List<TopicMetrics> saveAllMetrics(List<TopicMetrics> metricsList) {
        if (metricsList == null || metricsList.isEmpty()) {
            return List.of();
        }
        
        List<TopicMetricsEntity> entities = metricsList.stream()
                .map(TopicMetricsEntity::fromDomain)
                .collect(Collectors.toList());
        
        // Batch insert - JPA will optimize this if batch size is configured
        List<TopicMetricsEntity> saved = repository.saveAll(entities);
        
        log.debug("Saved {} topic metrics in batch", saved.size());
        
        return saved.stream()
                .map(TopicMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for a topic within time range - single query
     */
    public List<TopicMetrics> getMetrics(String clusterId, String topicName, 
                                         LocalDateTime startTime, LocalDateTime endTime, 
                                         int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<TopicMetricsEntity> entities = repository.findByClusterIdAndTopicNameAndTimestampBetween(
                clusterId, topicName, startTime, endTime, pageable);
        
        return entities.stream()
                .map(TopicMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for multiple topics - batch query to avoid N+1
     */
    public List<TopicMetrics> getMetricsForTopics(String clusterId, List<String> topicNames,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        List<TopicMetricsEntity> entities = repository.findByClusterIdAndTopicNamesInAndTimestampBetween(
                clusterId, topicNames, startTime, endTime);
        
        return entities.stream()
                .map(TopicMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get latest metrics for a topic
     */
    public TopicMetrics getLatestMetrics(String clusterId, String topicName) {
        Pageable pageable = PageRequest.of(0, 1);
        List<TopicMetricsEntity> entities = repository.findLatestByClusterIdAndTopicName(
                clusterId, topicName, pageable);
        
        if (entities.isEmpty()) {
            return null;
        }
        
        return entities.get(0).toDomain();
    }
    
    /**
     * Get aggregated metrics - single query with aggregation
     */
    public Object[] getAggregatedMetrics(String clusterId, String topicName,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        return repository.getAggregatedMetrics(clusterId, topicName, startTime, endTime);
    }
    
    /**
     * Delete old metrics for retention policy - batch delete
     */
    @Transactional
    public void deleteMetricsBefore(LocalDateTime cutoff) {
        repository.deleteByTimestampBefore(cutoff);
        log.info("Deleted topic metrics older than {}", cutoff);
    }
}

