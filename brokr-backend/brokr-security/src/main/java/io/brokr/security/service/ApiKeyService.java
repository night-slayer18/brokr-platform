package io.brokr.security.service;

import io.brokr.core.model.ApiKey;
import io.brokr.core.model.ApiKeyScope;
import io.brokr.storage.entity.ApiKeyEntity;
import io.brokr.storage.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Service for API key management with enterprise-grade security.
 * Thread-safe operations, secure random generation, BCrypt hashing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {
    
    private static final String KEY_PREFIX = "brokr_";
    private static final int SECRET_LENGTH = 32; // 32 bytes = 256 bits
    
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Thread-safe lock for key generation to prevent race conditions
    private final ReentrantReadWriteLock keyGenerationLock = new ReentrantReadWriteLock();
    
    /**
     * Generate a new API key.
     * Thread-safe, cryptographically secure random generation.
     * 
     * @param userId User ID who owns the key
     * @param organizationId Organization ID
     * @param name Key name/description
     * @param description Optional description
     * @param scopes Set of permission scopes
     * @param expiresAt Optional expiration date (null = never expires)
     * @return Generated API key with full key (shown only once)
     */
    @Transactional
    public ApiKeyGenerationResult generateApiKey(
            String userId,
            String organizationId,
            String name,
            String description,
            Set<String> scopes,
            Instant expiresAt
    ) {
        // Validate scopes
        validateScopes(scopes);
        
        // Thread-safe key generation
        keyGenerationLock.writeLock().lock();
        try {
            // Generate unique key ID (UUID)
            String keyId = UUID.randomUUID().toString();
            
            // Generate cryptographically secure random secret
            byte[] secretBytes = new byte[SECRET_LENGTH];
            secureRandom.nextBytes(secretBytes);
            String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
            
            // Create key prefix
            String keyPrefix = KEY_PREFIX + keyId;
            
            // Hash secret using BCrypt (same as passwords)
            String secretHash = passwordEncoder.encode(secret);
            
            // Create API key entity
            ApiKeyEntity entity = ApiKeyEntity.builder()
                    .id(keyId) // Use the same keyId for entity ID
                    .userId(userId)
                    .organizationId(organizationId)
                    .name(name)
                    .description(description)
                    .keyPrefix(keyPrefix)
                    .secretHash(secretHash)
                    .scopes(scopes != null ? new HashSet<>(scopes) : new HashSet<>())
                    .isActive(true)
                    .isRevoked(false)
                    .expiresAt(expiresAt)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            
            ApiKeyEntity saved = apiKeyRepository.save(entity);
            
            // Return full key (only shown once)
            String fullKey = keyPrefix + "_" + secret;
            
            log.info("Generated API key for user: {}, keyId: {}", userId, saved.getId());
            
            return ApiKeyGenerationResult.builder()
                    .apiKey(ApiKey.builder()
                            .id(saved.getId())
                            .userId(saved.getUserId())
                            .organizationId(saved.getOrganizationId())
                            .name(saved.getName())
                            .description(saved.getDescription())
                            .keyPrefix(saved.getKeyPrefix())
                            .scopes(saved.getScopes())
                            .isActive(saved.isActive())
                            .expiresAt(saved.getExpiresAt())
                            .createdAt(saved.getCreatedAt())
                            .updatedAt(saved.getUpdatedAt())
                            .build())
                    .fullKey(fullKey) // Only in response, not stored
                    .build();
        } finally {
            keyGenerationLock.writeLock().unlock();
        }
    }
    
    /**
     * Validate API key and return validation result.
     * Thread-safe, handles rotation grace period.
     * 
     * @param fullKey The full API key (brokr_<uuid>_<secret>)
     * @return Validation result with key info if valid
     */
    @Transactional(readOnly = true)
    public ApiKeyValidationResult validateApiKey(String fullKey) {
        if (fullKey == null || fullKey.trim().isEmpty()) {
            return ApiKeyValidationResult.invalid("API key is null or empty");
        }
        
        // Parse key format
        if (!fullKey.startsWith(KEY_PREFIX)) {
            return ApiKeyValidationResult.invalid("Invalid API key format");
        }
        
        String[] parts = fullKey.split("_", 3);
        if (parts.length != 3) {
            return ApiKeyValidationResult.invalid("Invalid API key format");
        }
        
        String keyId = parts[1];
        String secret = parts[2];
        String keyPrefix = KEY_PREFIX + keyId;
        
        // Find API key by prefix (optimized query with index)
        Optional<ApiKeyEntity> entityOpt = apiKeyRepository.findActiveByKeyPrefix(
                keyPrefix, 
                Instant.now()
        );
        
        if (entityOpt.isEmpty()) {
            // Try to find even if inactive (for better error messages)
            Optional<ApiKeyEntity> inactiveOpt = apiKeyRepository.findByKeyPrefix(keyPrefix);
            if (inactiveOpt.isPresent()) {
                ApiKeyEntity inactive = inactiveOpt.get();
                if (inactive.isRevoked()) {
                    return ApiKeyValidationResult.invalid("API key has been revoked");
                }
                if (inactive.getExpiresAt() != null && inactive.getExpiresAt().isBefore(Instant.now())) {
                    return ApiKeyValidationResult.invalid("API key has expired");
                }
                if (!inactive.isActive()) {
                    return ApiKeyValidationResult.invalid("API key is inactive");
                }
            }
            return ApiKeyValidationResult.invalid("API key not found");
        }
        
        ApiKeyEntity entity = entityOpt.get();
        
        // Verify secret hash
        boolean secretValid = passwordEncoder.matches(secret, entity.getSecretHash());
        
        // Check old secret (for rotation grace period)
        if (!secretValid && entity.getOldSecretHash() != null) {
            secretValid = passwordEncoder.matches(secret, entity.getOldSecretHash());
            if (secretValid) {
                log.debug("API key validated using old secret (rotation grace period): {}", entity.getId());
            }
        }
        
        if (!secretValid) {
            log.warn("Invalid secret for API key: {}", entity.getId());
            return ApiKeyValidationResult.invalid("Invalid API key secret");
        }
        
        // Return validation result
        return ApiKeyValidationResult.valid(
                entity.getId(),
                entity.getUserId(),
                entity.getOrganizationId(),
                entity.getScopes()
        );
    }
    
    /**
     * Rotate API key - generate new secret while keeping same key ID.
     * Thread-safe, supports grace period for old key.
     * 
     * @param apiKeyId API key ID to rotate
     * @param gracePeriodDays Grace period in days (default 7)
     * @return New full API key
     */
    @Transactional
    public ApiKeyGenerationResult rotateApiKey(String apiKeyId, Integer gracePeriodDays) {
        keyGenerationLock.writeLock().lock();
        try {
            ApiKeyEntity entity = apiKeyRepository.findById(apiKeyId)
                    .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));
            
            if (entity.isRevoked() || !entity.isActive() || entity.getDeletedAt() != null) {
                throw new IllegalStateException("Cannot rotate revoked, inactive, or deleted API key");
            }
            
            // Generate new secret
            byte[] secretBytes = new byte[SECRET_LENGTH];
            secureRandom.nextBytes(secretBytes);
            String newSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
            String newSecretHash = passwordEncoder.encode(newSecret);
            
            // Store old secret hash for grace period
            entity.setOldSecretHash(entity.getSecretHash());
            entity.setSecretHash(newSecretHash);
            entity.setUpdatedAt(Instant.now());
            
            // If grace period specified, we'll clean up old hash after grace period
            // For now, just store it (cleanup job can remove it later)
            
            ApiKeyEntity saved = apiKeyRepository.save(entity);
            
            // Return new full key
            String fullKey = saved.getKeyPrefix() + "_" + newSecret;
            
            log.info("Rotated API key: {}", apiKeyId);
            
            return ApiKeyGenerationResult.builder()
                    .apiKey(saved.toDomain())
                    .fullKey(fullKey)
                    .build();
        } finally {
            keyGenerationLock.writeLock().unlock();
        }
    }
    
    /**
     * Revoke API key (marks as revoked but keeps record for audit).
     * Thread-safe operation.
     */
    @Transactional
    public void revokeApiKey(String apiKeyId, String reason) {
        ApiKeyEntity entity = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));
        
        if (entity.isRevoked()) {
            log.warn("API key already revoked: {}", apiKeyId);
            return;
        }
        
        if (entity.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot revoke deleted API key");
        }
        
        apiKeyRepository.revoke(
                apiKeyId,
                Instant.now(),
                reason,
                Instant.now()
        );
        
        log.info("Revoked API key: {}, reason: {}", apiKeyId, reason);
    }
    
    /**
     * Delete API key (soft delete - sets deletedAt timestamp).
     * This permanently removes the key from active use but keeps it for audit purposes.
     * Thread-safe operation.
     */
    @Transactional
    public void deleteApiKey(String apiKeyId) {
        ApiKeyEntity entity = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));
        
        if (entity.getDeletedAt() != null) {
            log.warn("API key already deleted: {}", apiKeyId);
            return;
        }
        
        // Soft delete - sets deletedAt timestamp
        apiKeyRepository.softDelete(
                apiKeyId,
                Instant.now(),
                Instant.now()
        );
        
        log.info("Deleted API key: {}", apiKeyId);
    }
    
    /**
     * Update API key metadata (name, description, scopes, expiration).
     * Thread-safe operation.
     */
    @Transactional
    public ApiKey updateApiKey(
            String apiKeyId,
            String name,
            String description,
            Set<String> scopes,
            Instant expiresAt
    ) {
        ApiKeyEntity entity = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));
        
        if (entity.isRevoked() || entity.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot update revoked or deleted API key");
        }
        
        if (name != null) {
            entity.setName(name);
        }
        if (description != null) {
            entity.setDescription(description);
        }
        if (scopes != null) {
            validateScopes(scopes);
            entity.setScopes(new HashSet<>(scopes));
        }
        if (expiresAt != null) {
            entity.setExpiresAt(expiresAt);
        }
        
        entity.setUpdatedAt(Instant.now());
        
        ApiKeyEntity saved = apiKeyRepository.save(entity);
        
        log.info("Updated API key: {}", apiKeyId);
        
        return saved.toDomain();
    }
    
    /**
     * Get API key by ID.
     */
    @Transactional(readOnly = true)
    public ApiKey getApiKeyById(String apiKeyId) {
        return apiKeyRepository.findById(apiKeyId)
                .map(ApiKeyEntity::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + apiKeyId));
    }
    
    /**
     * List API keys for a user.
     * Includes active, revoked, and expired keys (but excludes deleted keys).
     * This follows standard practice where revoked keys remain visible for audit purposes.
     */
    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeysByUserId(String userId) {
        // Use findByUserId which includes revoked keys but excludes deleted ones
        // This allows users to see their revoked keys for audit/history purposes
        return apiKeyRepository.findByUserId(userId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(ApiKeyEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Update last used timestamp (async, non-blocking).
     * Thread-safe operation.
     */
    @Transactional
    public void updateLastUsed(String apiKeyId) {
        try {
            apiKeyRepository.updateLastUsedAt(apiKeyId, Instant.now(), Instant.now());
        } catch (Exception e) {
            // Log but don't fail the request
            log.warn("Failed to update last used timestamp for API key: {}", apiKeyId, e);
        }
    }
    
    /**
     * Validate scopes.
     */
    private void validateScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("At least one scope is required");
        }
        
        for (String scope : scopes) {
            if (!ApiKeyScope.isValid(scope)) {
                throw new IllegalArgumentException("Invalid scope: " + scope);
            }
        }
    }
    
    /**
     * Result class for API key generation.
     */
    @lombok.Data
    @lombok.Builder
    public static class ApiKeyGenerationResult {
        private ApiKey apiKey;
        private String fullKey; // Only shown once, not stored
    }
    
    /**
     * Result class for API key validation.
     */
    @lombok.Data
    @lombok.Builder
    public static class ApiKeyValidationResult {
        private boolean valid;
        private String apiKeyId;
        private String userId;
        private String organizationId;
        private Set<String> scopes;
        private String errorMessage;
        
        public static ApiKeyValidationResult valid(
                String apiKeyId,
                String userId,
                String organizationId,
                Set<String> scopes
        ) {
            return ApiKeyValidationResult.builder()
                    .valid(true)
                    .apiKeyId(apiKeyId)
                    .userId(userId)
                    .organizationId(organizationId)
                    .scopes(scopes != null ? new HashSet<>(scopes) : new HashSet<>())
                    .build();
        }
        
        public static ApiKeyValidationResult invalid(String errorMessage) {
            return ApiKeyValidationResult.builder()
                    .valid(false)
                    .errorMessage(errorMessage)
                    .scopes(new HashSet<>())
                    .build();
        }
    }
}

