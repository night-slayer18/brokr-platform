package io.brokr.storage.entity;

import io.brokr.core.model.ApiKeyUsage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for API key usage tracking.
 * Maps to the api_key_usage table.
 */
@Entity
@Table(name = "api_key_usage", indexes = {
    @Index(name = "idx_api_key_usage_key_id", columnList = "api_key_id,created_at DESC"),
    @Index(name = "idx_api_key_usage_user_id", columnList = "user_id,created_at DESC"),
    @Index(name = "idx_api_key_usage_org_id", columnList = "organization_id,created_at DESC"),
    @Index(name = "idx_api_key_usage_endpoint", columnList = "endpoint,created_at DESC"),
    @Index(name = "idx_api_key_usage_created_at", columnList = "created_at DESC"),
    @Index(name = "idx_api_key_usage_key_time", columnList = "api_key_id,created_at DESC"),
    @Index(name = "idx_api_key_usage_status", columnList = "status_code,created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUsageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "api_key_id", nullable = false)
    private String apiKeyId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "organization_id", nullable = false)
    private String organizationId;
    
    @Column(nullable = false)
    private String endpoint;
    
    @Column(nullable = false, length = 10)
    private String method;
    
    @Column(name = "status_code", nullable = false)
    private Integer statusCode;
    
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "request_size_bytes")
    private Integer requestSizeBytes;
    
    @Column(name = "response_size_bytes")
    private Integer responseSizeBytes;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public ApiKeyUsage toDomain() {
        return ApiKeyUsage.builder()
                .id(id)
                .apiKeyId(apiKeyId)
                .userId(userId)
                .organizationId(organizationId)
                .endpoint(endpoint)
                .method(method)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestSizeBytes(requestSizeBytes)
                .responseSizeBytes(responseSizeBytes)
                .errorMessage(errorMessage)
                .createdAt(createdAt)
                .build();
    }
    
    public static ApiKeyUsageEntity fromDomain(ApiKeyUsage usage) {
        return ApiKeyUsageEntity.builder()
                .id(usage.getId())
                .apiKeyId(usage.getApiKeyId())
                .userId(usage.getUserId())
                .organizationId(usage.getOrganizationId())
                .endpoint(usage.getEndpoint())
                .method(usage.getMethod())
                .statusCode(usage.getStatusCode())
                .responseTimeMs(usage.getResponseTimeMs())
                .ipAddress(usage.getIpAddress())
                .userAgent(usage.getUserAgent())
                .requestSizeBytes(usage.getRequestSizeBytes())
                .responseSizeBytes(usage.getResponseSizeBytes())
                .errorMessage(usage.getErrorMessage())
                .createdAt(usage.getCreatedAt() != null ? usage.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}

