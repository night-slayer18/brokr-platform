package io.brokr.storage.repository;

import io.brokr.storage.entity.ClusterMetricsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClusterMetricsRepository extends JpaRepository<ClusterMetricsEntity, String> {
    
    /**
     * Find metrics for a cluster within time range - optimized with single query
     * Uses index: idx_cluster_metrics_cluster_timestamp
     */
    @Query("SELECT c FROM ClusterMetricsEntity c " +
           "WHERE c.clusterId = :clusterId " +
           "AND c.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY c.timestamp ASC")
    List<ClusterMetricsEntity> findByClusterIdAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find latest metrics for a cluster - single query with LIMIT
     */
    @Query("SELECT c FROM ClusterMetricsEntity c " +
           "WHERE c.clusterId = :clusterId " +
           "ORDER BY c.timestamp DESC")
    List<ClusterMetricsEntity> findLatestByClusterId(
            @Param("clusterId") String clusterId,
            Pageable pageable
    );
    
    /**
     * Find metrics for multiple clusters - batch query to avoid N+1
     */
    @Query("SELECT c FROM ClusterMetricsEntity c " +
           "WHERE c.clusterId IN :clusterIds " +
           "AND c.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY c.clusterId, c.timestamp ASC")
    List<ClusterMetricsEntity> findByClusterIdsInAndTimestampBetween(
            @Param("clusterIds") List<String> clusterIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Aggregate cluster metrics for time range - single query with aggregation
     */
    @Query("SELECT " +
           "AVG(c.totalMessagesPerSecond) as avgMessagesPerSecond, " +
           "MAX(c.totalMessagesPerSecond) as maxMessagesPerSecond, " +
           "AVG(c.totalBytesPerSecond) as avgBytesPerSecond, " +
           "MAX(c.totalBytesPerSecond) as maxBytesPerSecond, " +
           "AVG(c.totalTopics) as avgTopics, " +
           "AVG(c.totalPartitions) as avgPartitions " +
           "FROM ClusterMetricsEntity c " +
           "WHERE c.clusterId = :clusterId " +
           "AND c.timestamp BETWEEN :startTime AND :endTime")
    Object[] getAggregatedMetrics(
            @Param("clusterId") String clusterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Delete old metrics (for retention policy) - batch delete
     */
    @Modifying
    @Query("DELETE FROM ClusterMetricsEntity c WHERE c.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
}

