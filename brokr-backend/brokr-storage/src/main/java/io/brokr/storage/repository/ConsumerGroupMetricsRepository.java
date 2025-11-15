package io.brokr.storage.repository;

import io.brokr.storage.entity.ConsumerGroupMetricsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ConsumerGroupMetricsRepository extends JpaRepository<ConsumerGroupMetricsEntity, String> {
    
    /**
     * Find metrics for a consumer group within time range - optimized with single query
     * Uses index: idx_consumer_group_metrics_cluster_group_timestamp
     */
    @Query("SELECT c FROM ConsumerGroupMetricsEntity c " +
           "WHERE c.clusterId = :clusterId AND c.consumerGroupId = :consumerGroupId " +
           "AND c.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY c.timestamp ASC")
    List<ConsumerGroupMetricsEntity> findByClusterIdAndConsumerGroupIdAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("consumerGroupId") String consumerGroupId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find latest metrics for a consumer group - single query with LIMIT
     */
    @Query("SELECT c FROM ConsumerGroupMetricsEntity c " +
           "WHERE c.clusterId = :clusterId AND c.consumerGroupId = :consumerGroupId " +
           "ORDER BY c.timestamp DESC")
    List<ConsumerGroupMetricsEntity> findLatestByClusterIdAndConsumerGroupId(
            @Param("clusterId") String clusterId,
            @Param("consumerGroupId") String consumerGroupId,
            Pageable pageable
    );
    
    /**
     * Find metrics for multiple consumer groups in a cluster - batch query to avoid N+1
     */
    @Query("SELECT c FROM ConsumerGroupMetricsEntity c " +
           "WHERE c.clusterId = :clusterId " +
           "AND c.consumerGroupId IN :consumerGroupIds " +
           "AND c.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY c.consumerGroupId, c.timestamp ASC")
    List<ConsumerGroupMetricsEntity> findByClusterIdAndConsumerGroupIdsInAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("consumerGroupIds") List<String> consumerGroupIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Aggregate lag metrics for time range - single query with aggregation
     */
    @Query("SELECT " +
           "AVG(c.totalLag) as avgTotalLag, " +
           "MAX(c.totalLag) as maxTotalLag, " +
           "MIN(c.totalLag) as minTotalLag, " +
           "AVG(c.maxLag) as avgMaxLag, " +
           "MAX(c.maxLag) as maxMaxLag " +
           "FROM ConsumerGroupMetricsEntity c " +
           "WHERE c.clusterId = :clusterId AND c.consumerGroupId = :consumerGroupId " +
           "AND c.timestamp BETWEEN :startTime AND :endTime")
    Object[] getAggregatedLagMetrics(
            @Param("clusterId") String clusterId,
            @Param("consumerGroupId") String consumerGroupId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Delete old metrics (for retention policy) - batch delete
     */
    @Modifying
    @Query("DELETE FROM ConsumerGroupMetricsEntity c WHERE c.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Count metrics for a cluster - for monitoring
     */
    @Query("SELECT COUNT(c) FROM ConsumerGroupMetricsEntity c WHERE c.clusterId = :clusterId")
    long countByClusterId(@Param("clusterId") String clusterId);
    
    /**
     * Find all consumer group metrics that reference a specific topic in their topic_lags JSONB
     * Used to clean up topic references when a topic is deleted
     * Uses native query with jsonb_exists function (PostgreSQL JSONB key exists check)
     */
    @Query(value = "SELECT * FROM consumer_group_metrics c " +
           "WHERE c.cluster_id = ?1 " +
           "AND c.topic_lags IS NOT NULL " +
           "AND jsonb_exists(c.topic_lags, ?2)",
           nativeQuery = true)
    List<ConsumerGroupMetricsEntity> findByClusterIdAndTopicInLags(
            String clusterId,
            String topicName
    );
}

