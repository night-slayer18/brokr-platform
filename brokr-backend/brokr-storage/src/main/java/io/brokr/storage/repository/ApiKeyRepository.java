package io.brokr.storage.repository;

import io.brokr.storage.entity.ApiKeyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for API keys with optimized queries.
 * Uses indexes for fast lookups.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {
    
    /**
     * Find API key by key prefix (for fast validation).
     * Uses index: idx_api_keys_prefix
     */
    Optional<ApiKeyEntity> findByKeyPrefix(String keyPrefix);
    
    /**
     * Find all active API keys for a user.
     * Uses index: idx_api_keys_user_active
     */
    @Query("SELECT k FROM ApiKeyEntity k " +
           "WHERE k.userId = :userId " +
           "AND k.isActive = true " +
           "AND k.isRevoked = false " +
           "AND k.deletedAt IS NULL " +
           "AND (k.expiresAt IS NULL OR k.expiresAt > :now) " +
           "ORDER BY k.createdAt DESC")
    List<ApiKeyEntity> findActiveByUserId(
            @Param("userId") String userId,
            @Param("now") Instant now
    );
    
    /**
     * Find all API keys for a user (including inactive/revoked).
     */
    @Query("SELECT k FROM ApiKeyEntity k " +
           "WHERE k.userId = :userId " +
           "AND k.deletedAt IS NULL " +
           "ORDER BY k.createdAt DESC")
    Page<ApiKeyEntity> findByUserId(
            @Param("userId") String userId,
            Pageable pageable
    );
    
    /**
     * Find all API keys for an organization.
     */
    @Query("SELECT k FROM ApiKeyEntity k " +
           "WHERE k.organizationId = :organizationId " +
           "AND k.deletedAt IS NULL " +
           "ORDER BY k.createdAt DESC")
    Page<ApiKeyEntity> findByOrganizationId(
            @Param("organizationId") String organizationId,
            Pageable pageable
    );
    
    /**
     * Find active API keys for validation (optimized query).
     * Uses index: idx_api_keys_active
     */
    @Query("SELECT k FROM ApiKeyEntity k " +
           "WHERE k.keyPrefix = :keyPrefix " +
           "AND k.isActive = true " +
           "AND k.isRevoked = false " +
           "AND k.deletedAt IS NULL " +
           "AND (k.expiresAt IS NULL OR k.expiresAt > :now)")
    Optional<ApiKeyEntity> findActiveByKeyPrefix(
            @Param("keyPrefix") String keyPrefix,
            @Param("now") Instant now
    );
    
    /**
     * Update last used timestamp (optimized update).
     */
    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.lastUsedAt = :lastUsedAt, k.updatedAt = :updatedAt " +
           "WHERE k.id = :id")
    void updateLastUsedAt(
            @Param("id") String id,
            @Param("lastUsedAt") Instant lastUsedAt,
            @Param("updatedAt") Instant updatedAt
    );
    
    /**
     * Soft delete API key.
     */
    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.deletedAt = :deletedAt, k.updatedAt = :updatedAt " +
           "WHERE k.id = :id")
    void softDelete(
            @Param("id") String id,
            @Param("deletedAt") Instant deletedAt,
            @Param("updatedAt") Instant updatedAt
    );
    
    /**
     * Revoke API key.
     */
    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.isRevoked = true, k.revokedAt = :revokedAt, " +
           "k.revokedReason = :reason, k.updatedAt = :updatedAt " +
           "WHERE k.id = :id")
    void revoke(
            @Param("id") String id,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason,
            @Param("updatedAt") Instant updatedAt
    );
    
    /**
     * Find expired API keys (for cleanup job).
     * Uses index: idx_api_keys_expires_at
     */
    @Query("SELECT k FROM ApiKeyEntity k " +
           "WHERE k.expiresAt IS NOT NULL " +
           "AND k.expiresAt < :now " +
           "AND k.isActive = true")
    List<ApiKeyEntity> findExpiredKeys(@Param("now") Instant now);
    
    /**
     * Count active API keys for a user.
     */
    @Query("SELECT COUNT(k) FROM ApiKeyEntity k " +
           "WHERE k.userId = :userId " +
           "AND k.isActive = true " +
           "AND k.isRevoked = false " +
           "AND k.deletedAt IS NULL")
    long countActiveByUserId(@Param("userId") String userId);
    
    /**
     * Count active API keys for an organization.
     */
    @Query("SELECT COUNT(k) FROM ApiKeyEntity k " +
           "WHERE k.organizationId = :organizationId " +
           "AND k.isActive = true " +
           "AND k.isRevoked = false " +
           "AND k.deletedAt IS NULL")
    long countActiveByOrganizationId(@Param("organizationId") String organizationId);
}

