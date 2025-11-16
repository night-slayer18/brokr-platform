package io.brokr.api.rest.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for API key management.
 * Provides REST endpoints for creating, managing, and monitoring API keys.
 * Keeps REST and GraphQL APIs in sync.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyUsageService usageService;
    private final ApiKeyRateLimitService rateLimitService;
    private final AuthorizationService authorizationService;
    
    /**
     * List all API keys for the current user.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ApiKey>> listApiKeys() {
        String userId = authorizationService.getCurrentUser().getId();
        List<ApiKey> apiKeys = apiKeyService.listApiKeysByUserId(userId);
        return ResponseEntity.ok(apiKeys);
    }
    
    /**
     * Get API key by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiKey> getApiKey(@PathVariable String id) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(key);
    }
    
    /**
     * Create a new API key.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.API_KEY, logResult = true)
    public ResponseEntity<Map<String, Object>> createApiKey(@RequestBody ApiKeyInput input) {
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
        
        Map<String, Object> response = new HashMap<>();
        response.put("apiKey", result.getApiKey());
        response.put("fullKey", result.getFullKey());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Update API key.
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ResponseEntity<ApiKey> updateApiKey(
            @PathVariable String id,
            @RequestBody ApiKeyUpdateInput input
    ) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Instant expiresAt = null;
        if (input.getExpiresAt() != null && !input.getExpiresAt().isEmpty()) {
            expiresAt = parseExpiresAt(input.getExpiresAt());
        }
        
        ApiKey updated = apiKeyService.updateApiKey(
                id,
                input.getName(),
                input.getDescription(),
                input.getScopes(),
                expiresAt
        );
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Revoke API key (marks as revoked but keeps record for audit).
     */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ResponseEntity<Map<String, Boolean>> revokeApiKey(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        String reason = body != null ? body.get("reason") : "Revoked by user";
        apiKeyService.revokeApiKey(id, reason);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Rotate API key.
     */
    @PostMapping("/{id}/rotate")
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ResponseEntity<Map<String, Object>> rotateApiKey(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Integer> body
    ) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Integer gracePeriodDays = body != null ? body.get("gracePeriodDays") : 7;
        ApiKeyService.ApiKeyGenerationResult result = apiKeyService.rotateApiKey(
                id,
                gracePeriodDays != null ? gracePeriodDays : 7
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("apiKey", result.getApiKey());
        response.put("fullKey", result.getFullKey());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete API key (soft delete - permanently removes from account).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ResponseEntity<Map<String, Boolean>> deleteApiKey(@PathVariable String id) {
        ApiKey existing = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        apiKeyService.deleteApiKey(id);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get API key usage statistics.
     */
    @GetMapping("/{id}/usage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiKeyUsageService.ApiKeyUsageStatistics> getApiKeyUsage(
            @PathVariable String id,
            @RequestParam String startTime,
            @RequestParam String endTime
    ) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        
        ApiKeyUsageService.ApiKeyUsageStatistics stats = usageService.getUsageStatistics(id, start, end);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get API key rate limits.
     */
    @GetMapping("/{id}/rate-limits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RateLimitConfig>> getApiKeyRateLimits(@PathVariable String id) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<RateLimitConfig> rateLimits = rateLimitService.getRateLimits(id);
        return ResponseEntity.ok(rateLimits);
    }
    
    /**
     * Configure API key rate limits.
     */
    @PutMapping("/{id}/rate-limits")
    @PreAuthorize("isAuthenticated()")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.API_KEY, resourceNameParam = "id", logResult = true)
    public ResponseEntity<List<RateLimitConfig>> configureApiKeyRateLimits(
            @PathVariable String id,
            @RequestBody List<RateLimitConfigInput> configs
    ) {
        ApiKey key = apiKeyService.getApiKeyById(id);
        String userId = authorizationService.getCurrentUser().getId();
        if (!key.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<RateLimitConfig> rateLimitConfigs = configs.stream()
                .map(input -> new RateLimitConfig(
                        null,
                        id,
                        input.getLimitType(),
                        input.getLimitValue(),
                        input.getWindowSeconds(),
                        null,
                        null
                ))
                .toList();
        
        rateLimitService.configureRateLimits(id, rateLimitConfigs);
        List<RateLimitConfig> updated = rateLimitService.getRateLimits(id);
        
        return ResponseEntity.ok(updated);
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
}

