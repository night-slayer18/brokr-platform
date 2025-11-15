package io.brokr.kafka.service;

import io.brokr.core.model.ConsumerGroupMetrics;
import io.brokr.storage.entity.ConsumerGroupMetricsEntity;
import io.brokr.storage.repository.ConsumerGroupMetricsRepository;
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
public class ConsumerGroupMetricsService {
    
    private final ConsumerGroupMetricsRepository repository;
    
    /**
     * Save a single metric - for individual saves
     */
    @Transactional
    public ConsumerGroupMetrics saveMetrics(ConsumerGroupMetrics metrics) {
        ConsumerGroupMetricsEntity entity = ConsumerGroupMetricsEntity.fromDomain(metrics);
        ConsumerGroupMetricsEntity saved = repository.save(entity);
        return saved.toDomain();
    }
    
    /**
     * Batch save metrics - optimized to avoid N+1 queries
     */
    @Transactional
    public List<ConsumerGroupMetrics> saveAllMetrics(List<ConsumerGroupMetrics> metricsList) {
        if (metricsList == null || metricsList.isEmpty()) {
            return List.of();
        }
        
        List<ConsumerGroupMetricsEntity> entities = metricsList.stream()
                .map(ConsumerGroupMetricsEntity::fromDomain)
                .collect(Collectors.toList());
        
        // Batch insert - JPA will optimize this if batch size is configured
        List<ConsumerGroupMetricsEntity> saved = repository.saveAll(entities);
        
        log.debug("Saved {} consumer group metrics in batch", saved.size());
        
        return saved.stream()
                .map(ConsumerGroupMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for a consumer group within time range - single query
     */
    public List<ConsumerGroupMetrics> getMetrics(String clusterId, String consumerGroupId,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ConsumerGroupMetricsEntity> entities = repository.findByClusterIdAndConsumerGroupIdAndTimestampBetween(
                clusterId, consumerGroupId, startTime, endTime, pageable);
        
        return entities.stream()
                .map(ConsumerGroupMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get metrics for multiple consumer groups - batch query to avoid N+1
     */
    public List<ConsumerGroupMetrics> getMetricsForConsumerGroups(String clusterId, List<String> consumerGroupIds,
                                                                  LocalDateTime startTime, LocalDateTime endTime) {
        List<ConsumerGroupMetricsEntity> entities = repository.findByClusterIdAndConsumerGroupIdsInAndTimestampBetween(
                clusterId, consumerGroupIds, startTime, endTime);
        
        return entities.stream()
                .map(ConsumerGroupMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Get latest metrics for a consumer group
     */
    public ConsumerGroupMetrics getLatestMetrics(String clusterId, String consumerGroupId) {
        Pageable pageable = PageRequest.of(0, 1);
        List<ConsumerGroupMetricsEntity> entities = repository.findLatestByClusterIdAndConsumerGroupId(
                clusterId, consumerGroupId, pageable);
        
        if (entities.isEmpty()) {
            return null;
        }
        
        return entities.get(0).toDomain();
    }
    
    /**
     * Get aggregated lag metrics - single query with aggregation
     */
    public Object[] getAggregatedLagMetrics(String clusterId, String consumerGroupId,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        return repository.getAggregatedLagMetrics(clusterId, consumerGroupId, startTime, endTime);
    }
    
    /**
     * Delete old metrics for retention policy - batch delete
     */
    @Transactional
    public void deleteMetricsBefore(LocalDateTime cutoff) {
        repository.deleteByTimestampBefore(cutoff);
        log.info("Deleted consumer group metrics older than {}", cutoff);
    }
    
    /**
     * Remove a topic from all consumer group metrics' topicLags when topic is deleted
     * This ensures old metrics don't reference deleted topics
     * For consumer groups consuming from multiple topics, only the deleted topic is removed
     */
    @Transactional
    public void removeTopicFromConsumerGroupMetrics(String clusterId, String topicName) {
        // Find all consumer group metrics that reference this topic
        List<ConsumerGroupMetricsEntity> metricsWithTopic = repository.findByClusterIdAndTopicInLags(clusterId, topicName);
        
        if (metricsWithTopic.isEmpty()) {
            log.debug("No consumer group metrics found referencing topic: {} in cluster: {}", topicName, clusterId);
            return;
        }
        
        int updatedCount = 0;
        for (ConsumerGroupMetricsEntity entity : metricsWithTopic) {
            if (entity.getTopicLags() != null && entity.getTopicLags().containsKey(topicName)) {
                // Remove the topic from topicLags map
                entity.getTopicLags().remove(topicName);
                
                // Recalculate aggregated lag metrics if topicLags is not empty
                if (!entity.getTopicLags().isEmpty()) {
                    long totalLag = entity.getTopicLags().values().stream().mapToLong(Long::longValue).sum();
                    long maxLag = entity.getTopicLags().values().stream().mapToLong(Long::longValue).max().orElse(0L);
                    long minLag = entity.getTopicLags().values().stream().mapToLong(Long::longValue).min().orElse(0L);
                    double avgLag = totalLag / (double) entity.getTopicLags().size();
                    
                    entity.setTotalLag(totalLag);
                    entity.setMaxLag(maxLag);
                    entity.setMinLag(minLag);
                    entity.setAvgLag((long) avgLag);
                } else {
                    // If no topics left, set all lags to 0
                    entity.setTotalLag(0L);
                    entity.setMaxLag(0L);
                    entity.setMinLag(0L);
                    entity.setAvgLag(0L);
                }
                
                updatedCount++;
            }
        }
        
        if (updatedCount > 0) {
            repository.saveAll(metricsWithTopic);
            log.info("Removed topic {} from {} consumer group metrics in cluster: {}", topicName, updatedCount, clusterId);
        }
    }
}

