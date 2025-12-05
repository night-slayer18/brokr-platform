package io.brokr.storage.repository;

import io.brokr.storage.entity.BrokerMetricsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for broker metrics with batch-optimized queries to prevent N+1 issues.
 */
@Repository
public interface BrokerMetricsRepository extends JpaRepository<BrokerMetricsEntity, String> {
    
    /**
     * Get all broker metrics for a cluster within a time range.
     * Single query - no N+1 issues.
     */
    @Query("SELECT b FROM BrokerMetricsEntity b WHERE b.clusterId = :clusterId " +
           "AND b.timestamp BETWEEN :startTime AND :endTime ORDER BY b.timestamp DESC")
    List<BrokerMetricsEntity> findByClusterIdAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
    
    /**
     * Get metrics for a specific broker within a time range.
     */
    @Query("SELECT b FROM BrokerMetricsEntity b WHERE b.clusterId = :clusterId " +
           "AND b.brokerId = :brokerId AND b.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY b.timestamp DESC")
    List<BrokerMetricsEntity> findByClusterIdAndBrokerIdAndTimestampBetween(
            @Param("clusterId") String clusterId,
            @Param("brokerId") int brokerId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
    
    /**
     * Get the latest metrics for all brokers in a cluster.
     * Uses subquery to find max timestamp per broker - single query for all brokers.
     */
    @Query("SELECT b FROM BrokerMetricsEntity b WHERE b.clusterId = :clusterId " +
           "AND b.timestamp = (SELECT MAX(b2.timestamp) FROM BrokerMetricsEntity b2 " +
           "WHERE b2.clusterId = b.clusterId AND b2.brokerId = b.brokerId)")
    List<BrokerMetricsEntity> findLatestByClusterId(@Param("clusterId") String clusterId);
    
    /**
     * Get the latest metrics for a specific broker.
     */
    @Query("SELECT b FROM BrokerMetricsEntity b WHERE b.clusterId = :clusterId " +
           "AND b.brokerId = :brokerId ORDER BY b.timestamp DESC")
    List<BrokerMetricsEntity> findLatestByClusterIdAndBrokerId(
            @Param("clusterId") String clusterId,
            @Param("brokerId") int brokerId,
            Pageable pageable);
    
    /**
     * Batch fetch metrics for multiple clusters.
     * Avoids N+1 when loading metrics for dashboard views.
     */
    @Query("SELECT b FROM BrokerMetricsEntity b WHERE b.clusterId IN :clusterIds " +
           "AND b.timestamp BETWEEN :startTime AND :endTime ORDER BY b.timestamp DESC")
    List<BrokerMetricsEntity> findByClusterIdsInAndTimestampBetween(
            @Param("clusterIds") List<String> clusterIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * Delete old metrics for retention policy - batch delete.
     */
    @Modifying
    @Query("DELETE FROM BrokerMetricsEntity b WHERE b.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Count metrics by cluster - for monitoring.
     */
    long countByClusterId(String clusterId);
}
