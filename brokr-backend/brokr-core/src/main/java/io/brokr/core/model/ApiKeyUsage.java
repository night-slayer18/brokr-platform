package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain model for API key usage tracking.
 * Represents a single API request made with an API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUsage {
    private Long id;
    private String apiKeyId;
    private String userId;
    private String organizationId;
    private String endpoint; // e.g., "/api/clusters", "/graphql"
    private String method; // GET, POST, PUT, DELETE, etc.
    private Integer statusCode;
    private Integer responseTimeMs;
    private String ipAddress; // IPv4 or IPv6
    private String userAgent;
    private Integer requestSizeBytes;
    private Integer responseSizeBytes;
    private String errorMessage;
    private Instant createdAt;
}

