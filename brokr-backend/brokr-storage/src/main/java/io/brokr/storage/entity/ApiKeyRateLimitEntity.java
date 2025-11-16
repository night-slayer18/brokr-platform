package io.brokr.storage.entity;

import io.brokr.core.model.RateLimitConfig;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for API key rate limit configuration.
 * Maps to the api_key_rate_limits table.
 */
@Entity
@Table(name = "api_key_rate_limits", 
       uniqueConstraints = @UniqueConstraint(name = "unique_key_limit_type", columnNames = {"api_key_id", "limit_type"}),
       indexes = {
           @Index(name = "idx_rate_limits_key_id", columnList = "api_key_id"),
           @Index(name = "idx_rate_limits_type", columnList = "limit_type")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRateLimitEntity {
    
    @Id
    private String id;
    
    @Column(name = "api_key_id", nullable = false)
    private String apiKeyId;
    
    @Column(name = "limit_type", nullable = false, length = 20)
    private String limitType;
    
    @Column(name = "limit_value", nullable = false)
    private Integer limitValue;
    
    @Column(name = "window_seconds", nullable = false)
    private Integer windowSeconds;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    public RateLimitConfig toDomain() {
        return RateLimitConfig.builder()
                .id(id)
                .apiKeyId(apiKeyId)
                .limitType(limitType)
                .limitValue(limitValue)
                .windowSeconds(windowSeconds)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
    
    public static ApiKeyRateLimitEntity fromDomain(RateLimitConfig config) {
        return ApiKeyRateLimitEntity.builder()
                .id(config.getId())
                .apiKeyId(config.getApiKeyId())
                .limitType(config.getLimitType())
                .limitValue(config.getLimitValue())
                .windowSeconds(config.getWindowSeconds())
                .createdAt(config.getCreatedAt() != null ? config.getCreatedAt() : Instant.now())
                .updatedAt(config.getUpdatedAt() != null ? config.getUpdatedAt() : Instant.now())
                .build();
    }
}

