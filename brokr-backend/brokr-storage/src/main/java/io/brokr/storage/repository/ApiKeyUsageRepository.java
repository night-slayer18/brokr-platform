package io.brokr.storage.repository;

import io.brokr.storage.entity.ApiKeyUsageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for API key usage tracking.
 * Optimized queries for analytics and monitoring.
 */
@Repository
public interface ApiKeyUsageRepository extends JpaRepository<ApiKeyUsageEntity, Long> {
    
    /**
     * Find usage records for an API key within time range.
     * Uses index: idx_api_key_usage_key_time
     */
    @Query("SELECT u FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY u.createdAt DESC")
    Page<ApiKeyUsageEntity> findByApiKeyIdAndCreatedAtBetween(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );
    
    /**
     * Find usage records for a user within time range.
     * Uses index: idx_api_key_usage_user_id
     */
    @Query("SELECT u FROM ApiKeyUsageEntity u " +
           "WHERE u.userId = :userId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY u.createdAt DESC")
    Page<ApiKeyUsageEntity> findByUserIdAndCreatedAtBetween(
            @Param("userId") String userId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );
    
    /**
     * Find usage records for an organization within time range.
     * Uses index: idx_api_key_usage_org_id
     */
    @Query("SELECT u FROM ApiKeyUsageEntity u " +
           "WHERE u.organizationId = :organizationId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY u.createdAt DESC")
    Page<ApiKeyUsageEntity> findByOrganizationIdAndCreatedAtBetween(
            @Param("organizationId") String organizationId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );
    
    /**
     * Count requests for an API key within time range.
     */
    @Query("SELECT COUNT(u) FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime")
    long countByApiKeyIdAndCreatedAtBetween(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Count requests by status code for an API key.
     * Uses index: idx_api_key_usage_status
     */
    @Query("SELECT u.statusCode, COUNT(u) FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY u.statusCode")
    List<Object[]> countByApiKeyIdAndStatusCode(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Count requests by endpoint for an API key.
     * Uses index: idx_api_key_usage_endpoint
     */
    @Query("SELECT u.endpoint, COUNT(u) FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "GROUP BY u.endpoint " +
           "ORDER BY COUNT(u) DESC")
    List<Object[]> countByApiKeyIdAndEndpoint(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );
    
    /**
     * Get average response time for an API key.
     */
    @Query("SELECT AVG(u.responseTimeMs) FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "AND u.responseTimeMs IS NOT NULL")
    Double getAverageResponseTime(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Get error rate for an API key.
     */
    @Query("SELECT COUNT(u) FROM ApiKeyUsageEntity u " +
           "WHERE u.apiKeyId = :apiKeyId " +
           "AND u.createdAt BETWEEN :startTime AND :endTime " +
           "AND u.statusCode >= 400")
    long countErrors(
            @Param("apiKeyId") String apiKeyId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
    
    /**
     * Delete old usage records (for data retention).
     * Uses index: idx_api_key_usage_created_at
     */
    @Modifying
    @Query("DELETE FROM ApiKeyUsageEntity u " +
           "WHERE u.createdAt < :cutoffTime")
    void deleteOldRecords(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Batch insert usage records (for performance).
     * Note: Spring Data JPA saveAll() is already optimized, but this can be used for custom batch inserts.
     */
    // Spring Data JPA's saveAll() already handles batch inserts efficiently
}

