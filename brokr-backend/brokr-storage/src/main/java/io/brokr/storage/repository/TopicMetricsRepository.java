package io.brokr.storage.repository;

import io.brokr.storage.entity.TopicMetricsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TopicMetricsRepository extends JpaRepository<TopicMetricsEntity, String> {
    
    /**
     * Find metrics for a topic within time range - optimized with single query
     * Uses index: idx_topic_metrics_cluster_topic_timestamp
     */
    @Query("SELECT t FROM TopicMetricsEntity t " +
           "WHERE t.clusterId = :clusterId AND t.topicName = :topicName " +
           "AND t.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY t.timestamp ASC")
    List<TopicMetricsEntity> findByClusterIdAndTopicNameAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("topicName") String topicName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find latest metrics for a topic - single query with LIMIT
     */
    @Query("SELECT t FROM TopicMetricsEntity t " +
           "WHERE t.clusterId = :clusterId AND t.topicName = :topicName " +
           "ORDER BY t.timestamp DESC")
    List<TopicMetricsEntity> findLatestByClusterIdAndTopicName(
            @Param("clusterId") String clusterId,
            @Param("topicName") String topicName,
            Pageable pageable
    );
    
    /**
     * Find metrics for multiple topics in a cluster - batch query to avoid N+1
     * Uses index: idx_topic_metrics_cluster_topic_timestamp
     */
    @Query("SELECT t FROM TopicMetricsEntity t " +
           "WHERE t.clusterId = :clusterId " +
           "AND t.topicName IN :topicNames " +
           "AND t.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY t.topicName, t.timestamp ASC")
    List<TopicMetricsEntity> findByClusterIdAndTopicNamesInAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("topicNames") List<String> topicNames,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Aggregate metrics (average, max, min) for time range - single query with aggregation
     */
    @Query("SELECT " +
           "AVG(t.messagesPerSecondIn) as avgMsgIn, " +
           "MAX(t.messagesPerSecondIn) as maxMsgIn, " +
           "MIN(t.messagesPerSecondIn) as minMsgIn, " +
           "AVG(t.bytesPerSecondIn) as avgBytesIn, " +
           "MAX(t.bytesPerSecondIn) as maxBytesIn " +
           "FROM TopicMetricsEntity t " +
           "WHERE t.clusterId = :clusterId AND t.topicName = :topicName " +
           "AND t.timestamp BETWEEN :startTime AND :endTime")
    Object[] getAggregatedMetrics(
            @Param("clusterId") String clusterId,
            @Param("topicName") String topicName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Delete old metrics (for retention policy) - batch delete
     */
    @Modifying
    @Query("DELETE FROM TopicMetricsEntity t WHERE t.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Count metrics for a cluster - for monitoring
     */
    @Query("SELECT COUNT(t) FROM TopicMetricsEntity t WHERE t.clusterId = :clusterId")
    long countByClusterId(@Param("clusterId") String clusterId);
    
    /**
     * Delete all metrics for a specific topic - used when topic is deleted
     * Batch delete for performance
     */
    @Modifying
    @Query("DELETE FROM TopicMetricsEntity t WHERE t.clusterId = :clusterId AND t.topicName = :topicName")
    void deleteByClusterIdAndTopicName(@Param("clusterId") String clusterId, @Param("topicName") String topicName);
}

