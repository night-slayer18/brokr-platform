package io.brokr.storage.entity;

import io.brokr.core.model.ApiKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity for API keys.
 * Maps to the api_keys table.
 */
@Entity
@Table(name = "api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyEntity {
    
    @Id
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "organization_id", nullable = false)
    private String organizationId;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(name = "key_prefix", nullable = false, unique = true, length = 50)
    private String keyPrefix;
    
    @Column(name = "secret_hash", nullable = false)
    private String secretHash;
    
    @Column(name = "old_secret_hash")
    private String oldSecretHash;
    
    @Column(name = "scopes", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private Set<String> scopes = new HashSet<>();
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
    
    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoked_reason")
    private String revokedReason;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    public ApiKey toDomain() {
        return ApiKey.builder()
                .id(id)
                .userId(userId)
                .organizationId(organizationId)
                .name(name)
                .description(description)
                .keyPrefix(keyPrefix)
                .secretHash(secretHash)
                .oldSecretHash(oldSecretHash)
                .scopes(scopes != null ? new HashSet<>(scopes) : new HashSet<>())
                .isActive(isActive)
                .isRevoked(isRevoked)
                .revokedAt(revokedAt)
                .revokedReason(revokedReason)
                .expiresAt(expiresAt)
                .lastUsedAt(lastUsedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .deletedAt(deletedAt)
                .build();
    }
    
    public static ApiKeyEntity fromDomain(ApiKey apiKey) {
        return ApiKeyEntity.builder()
                .id(apiKey.getId())
                .userId(apiKey.getUserId())
                .organizationId(apiKey.getOrganizationId())
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .keyPrefix(apiKey.getKeyPrefix())
                .secretHash(apiKey.getSecretHash())
                .oldSecretHash(apiKey.getOldSecretHash())
                .scopes(apiKey.getScopes() != null ? new HashSet<>(apiKey.getScopes()) : new HashSet<>())
                .isActive(apiKey.isActive())
                .isRevoked(apiKey.isRevoked())
                .revokedAt(apiKey.getRevokedAt())
                .revokedReason(apiKey.getRevokedReason())
                .expiresAt(apiKey.getExpiresAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .deletedAt(apiKey.getDeletedAt())
                .build();
    }
}

