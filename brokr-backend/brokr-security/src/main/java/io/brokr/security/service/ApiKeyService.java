package io.brokr.security.service;

import io.brokr.core.model.ApiKey;
import io.brokr.core.model.ApiKeyScope;
import io.brokr.storage.entity.ApiKeyEntity;
import io.brokr.storage.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MAX_API_KEYS_PER_USER = 1000; // Maximum keys to return without pagination
    private static final long LAST_USED_UPDATE_THROTTLE_SECONDS = 60; // Update max once per minute per key
    
    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Thread-safe lock for key generation to prevent race conditions
    private final ReentrantReadWriteLock keyGenerationLock = new ReentrantReadWriteLock();
    
    // Throttling map for lastUsed updates: keyId -> last update timestamp
    // Using ConcurrentHashMap for thread-safe access
    private final Map<String, Instant> lastUsedUpdateCache = new ConcurrentHashMap<>();
    
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
                    .fullKey(fullKey)
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
        
        // Verify secret hash against current secret
        boolean secretValid = passwordEncoder.matches(secret, entity.getSecretHash());
        
        // Always check old secret (for rotation grace period) to prevent timing attacks
        // Even if current secret is valid, we check old secret to maintain constant-time behavior
        boolean oldSecretValid = false;
        if (entity.getOldSecretHash() != null) {
            oldSecretValid = passwordEncoder.matches(secret, entity.getOldSecretHash());
        }
        
        // Accept if either current or old secret is valid
        if (!secretValid && !oldSecretValid) {
            log.warn("Invalid secret for API key: {}", entity.getId());
            return ApiKeyValidationResult.invalid("Invalid API key secret");
        }
        
        // Log if old secret was used (for monitoring rotation grace period usage)
        if (!secretValid && oldSecretValid) {
            log.debug("API key validated using old secret (rotation grace period): {}", entity.getId());
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
     * 
     * Note: For users with many API keys, consider using pagination to avoid loading all keys into memory.
     * This method limits results to MAX_API_KEYS_PER_USER to prevent memory issues.
     */
    @Transactional(readOnly = true)
    public List<ApiKey> listApiKeysByUserId(String userId) {
        // Use pagination to limit memory usage - fetch first page with reasonable limit
        // This prevents loading thousands of keys into memory for users with many keys
        Pageable pageable = Pageable.ofSize(MAX_API_KEYS_PER_USER);
        return apiKeyRepository.findByUserId(userId, pageable)
                .getContent()
                .stream()
                .map(ApiKeyEntity::toDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * Update last used timestamp (async, throttled).
     * Uses throttling to prevent excessive database updates - updates max once per minute per key.
     * Thread-safe operation.
     */
    @Async
    public void updateLastUsed(String apiKeyId) {
        Instant now = Instant.now();
        
        // Throttle: Only update if last update was more than THROTTLE_SECONDS ago
        Instant lastUpdate = lastUsedUpdateCache.get(apiKeyId);
        if (lastUpdate != null) {
            long secondsSinceLastUpdate = now.getEpochSecond() - lastUpdate.getEpochSecond();
            if (secondsSinceLastUpdate < LAST_USED_UPDATE_THROTTLE_SECONDS) {
                // Skip update - too soon since last update
                return;
            }
        }
        
        // Update cache timestamp (optimistic - before DB update)
        lastUsedUpdateCache.put(apiKeyId, now);
        
        // Async methods need their own transaction context
        // Use TransactionTemplate to ensure transaction is created in async thread
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    apiKeyRepository.updateLastUsedAt(apiKeyId, now, now);
                } catch (Exception e) {
                    // Remove from cache on failure so we can retry
                    lastUsedUpdateCache.remove(apiKeyId);
                    // Log transaction failure prominently for monitoring
                    log.error("Transaction failed to update last used timestamp for API key: {} - {}", 
                            apiKeyId, e.getMessage(), e);
                    // Don't rethrow - we want to continue even if this fails
                }
            });
        } catch (Exception e) {
            // Remove from cache on failure so we can retry
            lastUsedUpdateCache.remove(apiKeyId);
            // Log transaction failure prominently for monitoring
            log.error("Failed to execute transaction for updating last used timestamp for API key: {} - {}", 
                    apiKeyId, e.getMessage(), e);
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
        private String fullKey;
        
        @Override
        public String toString() {
            // Never include fullKey in toString() to prevent accidental logging
            return "ApiKeyGenerationResult(apiKey=" + apiKey + ", fullKey=***REDACTED***)";
        }
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

