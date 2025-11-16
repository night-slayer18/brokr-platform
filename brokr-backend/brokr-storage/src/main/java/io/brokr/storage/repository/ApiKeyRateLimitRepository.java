package io.brokr.storage.repository;

import io.brokr.storage.entity.ApiKeyRateLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for API key rate limit configuration.
 */
@Repository
public interface ApiKeyRateLimitRepository extends JpaRepository<ApiKeyRateLimitEntity, String> {
    
    /**
     * Find all rate limits for an API key.
     * Uses index: idx_rate_limits_key_id
     */
    List<ApiKeyRateLimitEntity> findByApiKeyId(String apiKeyId);
    
    /**
     * Find rate limit by API key ID and limit type.
     * Uses unique constraint: unique_key_limit_type
     */
    Optional<ApiKeyRateLimitEntity> findByApiKeyIdAndLimitType(String apiKeyId, String limitType);
    
    /**
     * Delete all rate limits for an API key.
     */
    @Modifying
    @Query("DELETE FROM ApiKeyRateLimitEntity r WHERE r.apiKeyId = :apiKeyId")
    void deleteByApiKeyId(@Param("apiKeyId") String apiKeyId);
    
    /**
     * Delete rate limit by API key ID and limit type.
     */
    @Modifying
    @Query("DELETE FROM ApiKeyRateLimitEntity r " +
           "WHERE r.apiKeyId = :apiKeyId AND r.limitType = :limitType")
    void deleteByApiKeyIdAndLimitType(
            @Param("apiKeyId") String apiKeyId,
            @Param("limitType") String limitType
    );
}

