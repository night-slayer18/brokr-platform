package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.ApiKeyInput;
import io.brokr.api.input.ApiKeyUpdateInput;
import io.brokr.api.input.RateLimitConfigInput;
import io.brokr.core.model.ApiKey;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.RateLimitConfig;
import io.brokr.security.service.ApiKeyRateLimitService;
import io.brokr.security.service.ApiKeyService;
import io.brokr.security.service.ApiKeyUsageService;
import io.brokr.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL resolver for API key management.
 * Provides queries and mutations for creating, managing, and monitoring API keys.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ApiKeyResolver {
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyUsageService usageService;
    private final ApiKeyRateLimitService rateLimitService;
    private final AuthorizationService authorizationService;
    
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<ApiKey> apiKeys() {
        // Users can only see their own API keys
        String userId = authorizationService.getCurrentUser().getId();
        return apiKeyService.listApiKeysByUserId(userId);
    }
    
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public ApiKey apiKey(@Argument String id) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        // Ensure user can only access their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        return key;
    }
    
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public ApiKeyUsageService.ApiKeyUsageStatistics apiKeyUsage(
            @Argument String id,
            @Argument String startTime,
            @Argument String endTime
    ) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        // Ensure user can only access their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        
        // Security: Enforce maximum date range to prevent full-table scans
        // Limit to 30 days to prevent abuse and database overload
        java.time.Duration duration = java.time.Duration.between(start, end);
        if (duration.toDays() > 30) {
            throw new IllegalArgumentException(
                "Date range cannot exceed 30 days. Requested range: " + duration.toDays() + " days"
            );
        }
        
        // Ensure end is after start
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        return usageService.getUsageStatistics(id, start, end);
    }
    
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<RateLimitConfig> apiKeyRateLimits(@Argument String apiKeyId) {
        ApiKey key = apiKeyService.getApiKeyById(apiKeyId);
        // Ensure user can only access their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + apiKeyId
            );
        }
        
        return rateLimitService.getRateLimits(apiKeyId);
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "input.name", logResult = true)
    public ApiKeyGenerationResult createApiKey(@Argument ApiKeyInput input) {
        String userId = authorizationService.getCurrentUser().getId();
        String organizationId = authorizationService.getCurrentUser().getOrganizationId();
        
        Instant expiresAt = null;
        if (input.getExpiresAt() != null && !input.getExpiresAt().isEmpty()) {
            expiresAt = parseExpiresAt(input.getExpiresAt());
        }
        
        ApiKeyService.ApiKeyGenerationResult result = apiKeyService.generateApiKey(
                userId,
                organizationId,
                input.getName(),
                input.getDescription(),
                input.getScopes(),
                expiresAt
        );
        
        // Convert to GraphQL type
        return new ApiKeyGenerationResult(result.getApiKey(), result.getFullKey());
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ApiKey updateApiKey(@Argument String id, @Argument ApiKeyUpdateInput input) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        // Ensure user can only update their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        
        Instant expiresAt = null;
        if (input.getExpiresAt() != null && !input.getExpiresAt().isEmpty()) {
            expiresAt = parseExpiresAt(input.getExpiresAt());
        }
        
        return apiKeyService.updateApiKey(
                id,
                input.getName(),
                input.getDescription(),
                input.getScopes(),
                expiresAt
        );
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public Boolean revokeApiKey(@Argument String id, @Argument String reason) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        // Ensure user can only revoke their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        
        apiKeyService.revokeApiKey(id, reason);
        return true;
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ApiKeyGenerationResult rotateApiKey(
            @Argument String id,
            @Argument Integer gracePeriodDays
    ) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        // Ensure user can only rotate their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        
        ApiKeyService.ApiKeyGenerationResult result = apiKeyService.rotateApiKey(
                id,
                gracePeriodDays != null ? gracePeriodDays : 7
        );
        
        // Convert to GraphQL type
        return new ApiKeyGenerationResult(result.getApiKey(), result.getFullKey());
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public Boolean deleteApiKey(@Argument String id) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        // Ensure user can only delete their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + id
            );
        }
        
        // Soft delete (sets deletedAt timestamp)
        apiKeyService.deleteApiKey(id);
        return true;
    }
    
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "apiKeyId", logResult = true)
    public List<RateLimitConfig> configureApiKeyRateLimits(
            @Argument String apiKeyId,
            @Argument List<RateLimitConfigInput> configs
    ) {
        ApiKey existing = apiKeyService.getApiKeyById(apiKeyId);
        // Ensure user can only configure their own keys
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to API key: " + apiKeyId
            );
        }
        
        List<RateLimitConfig> rateLimitConfigs = configs.stream()
                .map(input -> RateLimitConfig.builder()
                        .apiKeyId(apiKeyId)
                        .limitType(input.getLimitType())
                        .limitValue(input.getLimitValue())
                        .windowSeconds(input.getWindowSeconds())
                        .build())
                .collect(Collectors.toList());
        
        rateLimitService.configureRateLimits(apiKeyId, rateLimitConfigs);
        
        return rateLimitService.getRateLimits(apiKeyId);
    }
    
    /**
     * Parse expiration date string.
     * Handles both ISO-8601 format (with timezone) and datetime-local format (YYYY-MM-DDTHH:mm).
     */
    private Instant parseExpiresAt(String expiresAtStr) {
        if (expiresAtStr == null || expiresAtStr.isEmpty()) {
            return null;
        }
        
        try {
            // Try ISO-8601 format first (e.g., "2025-11-23T22:16:00Z" or "2025-11-23T22:16:00.000Z")
            return Instant.parse(expiresAtStr);
        } catch (DateTimeParseException e) {
            // If that fails, try datetime-local format (e.g., "2025-11-23T22:16")
            // This format is missing seconds and timezone
            try {
                // Check if it matches datetime-local pattern: YYYY-MM-DDTHH:mm
                if (expiresAtStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
                    // Append :00 for seconds and parse as LocalDateTime, then convert to Instant
                    LocalDateTime localDateTime = LocalDateTime.parse(expiresAtStr + ":00");
                    // Convert to Instant using system default timezone
                    return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
                } else {
                    throw new IllegalArgumentException("Invalid date format: " + expiresAtStr + 
                            ". Expected ISO-8601 (e.g., 2025-11-23T22:16:00Z) or datetime-local (e.g., 2025-11-23T22:16)");
                }
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Invalid date format: " + expiresAtStr + 
                        ". Expected ISO-8601 (e.g., 2025-11-23T22:16:00Z) or datetime-local (e.g., 2025-11-23T22:16)", e2);
            }
        }
    }
    
    /**
     * Result class for API key generation (matches GraphQL type).
     */
    public static class ApiKeyGenerationResult {
        private final ApiKey apiKey;
        private final String fullKey;
        
        public ApiKeyGenerationResult(ApiKey apiKey, String fullKey) {
            this.apiKey = apiKey;
            this.fullKey = fullKey;
        }
        
        public ApiKey getApiKey() {
            return apiKey;
        }
        
        public String getFullKey() {
            return fullKey;
        }
    }
}

