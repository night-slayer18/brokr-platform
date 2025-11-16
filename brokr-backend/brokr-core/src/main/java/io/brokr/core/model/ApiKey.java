package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Domain model for API keys.
 * Represents an API key with its metadata, scopes, and status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    private String id;
    private String userId;
    private String organizationId;
    private String name;
    private String description;
    private String keyPrefix; // "brokr_<uuid>" part
    private String secretHash; // BCrypt hash of secret
    private String oldSecretHash; // For rotation grace period
    private Set<String> scopes; // Set of permission scopes
    private boolean isActive;
    private boolean isRevoked;
    private Instant revokedAt;
    private String revokedReason;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt; // Soft delete
    
    /**
     * Check if the API key is expired.
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    /**
     * Check if the API key is valid (active, not revoked, not expired).
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return isActive && !isRevoked && !isExpired() && deletedAt == null;
    }
    
    /**
     * Check if the API key has a specific scope.
     * @param scope The scope to check (e.g., "clusters:read")
     * @return true if the key has the scope, false otherwise
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
    
    /**
     * Check if the API key has any of the required scopes.
     * @param requiredScopes The required scopes
     * @return true if the key has at least one required scope, false otherwise
     */
    public boolean hasAnyScope(String... requiredScopes) {
        if (scopes == null || requiredScopes == null) {
            return false;
        }
        for (String requiredScope : requiredScopes) {
            if (scopes.contains(requiredScope)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if the API key has all of the required scopes.
     * @param requiredScopes The required scopes
     * @return true if the key has all required scopes, false otherwise
     */
    public boolean hasAllScopes(String... requiredScopes) {
        if (scopes == null || requiredScopes == null) {
            return false;
        }
        for (String requiredScope : requiredScopes) {
            if (!scopes.contains(requiredScope)) {
                return false;
            }
        }
        return true;
    }
}

