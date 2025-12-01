package io.brokr.security.service;

import io.brokr.core.exception.UnauthorizedException;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.security.model.ApiKeyAuthenticationToken;
import io.brokr.security.model.BrokrUserDetails;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.entity.KsqlDBEntity;
import io.brokr.storage.entity.SchemaRegistryEntity;
import io.brokr.storage.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final KafkaClusterRepository clusterRepository;
    private final SchemaRegistryRepository schemaRegistryRepository;
    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaStreamsApplicationRepository kafkaStreamsApplicationRepository;
    private final KsqlDBRepository ksqlDBRepository;
    private final JwtService jwtService;

    // Cache for cluster-to-environment mappings to avoid N+1 queries
    private final Map<String, ClusterMappingCacheEntry> clusterMappingCache = new ConcurrentHashMap<>();
    private static final long CLUSTER_MAPPING_CACHE_TTL_SECONDS = 600; // 10 minutes

    // Cache for resource-to-cluster mappings
    private final Map<String, ResourceMappingCacheEntry> resourceMappingCache = new ConcurrentHashMap<>();
    private static final long RESOURCE_MAPPING_CACHE_TTL_SECONDS = 600; // 10 minutes

    private static class ClusterMappingCacheEntry {
        final String environmentId;
        final Instant expiresAt;

        ClusterMappingCacheEntry(String environmentId, Instant expiresAt) {
            this.environmentId = environmentId;
            this.expiresAt = expiresAt;
        }
    }

    private static class ResourceMappingCacheEntry {
        final String clusterId;
        final Instant expiresAt;

        ResourceMappingCacheEntry(String clusterId, Instant expiresAt) {
            this.clusterId = clusterId;
            this.expiresAt = expiresAt;
        }
    }


    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        
        // Handle API key authentication
        if (authentication instanceof ApiKeyAuthenticationToken) {
            ApiKeyAuthenticationToken apiKeyAuth = (ApiKeyAuthenticationToken) authentication;
            Object principal = apiKeyAuth.getPrincipal();
            if (principal instanceof BrokrUserDetails) {
                return ((BrokrUserDetails) principal).getUser();
            }
            // This should never happen - ApiKeyAuthenticationFilter always sets BrokrUserDetails
            throw new IllegalStateException(
                    String.format("Unexpected principal type in API key authentication: %s. " +
                            "Expected BrokrUserDetails. This indicates a bug in authentication setup.",
                            principal != null ? principal.getClass().getName() : "null"));
        }
        
        // Handle JWT authentication (existing flow)
        Object principal = authentication.getPrincipal();
        if (principal instanceof BrokrUserDetails) {
            return ((BrokrUserDetails) principal).getUser();
        }

        // This should never happen - JwtAuthenticationFilter always sets BrokrUserDetails
        // If principal is a String (e.g. "anonymousUser"), it means the user is not authenticated
        // Throw UnauthorizedException so the frontend receives a 401/Unauthorized error and can redirect to login
        throw new UnauthorizedException("Unauthorized: User is not authenticated. Please log in again.");
    }
    
    /**
     * Get API key authentication token if present.
     */
    private ApiKeyAuthenticationToken getApiKeyAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ApiKeyAuthenticationToken) {
            return (ApiKeyAuthenticationToken) authentication;
        }
        return null;
    }
    
    /**
     * Check API key scopes. Throws AccessDeniedException if scope missing.
     * Only checks if authenticated via API key; JWT uses role-based auth.
     */
    private void checkApiKeyScope(String... requiredScopes) {
        ApiKeyAuthenticationToken apiKeyAuth = getApiKeyAuthentication();
        if (apiKeyAuth == null) {
            // Not API key auth, skip scope check (JWT uses role-based auth)
            return;
        }
        
        if (!apiKeyAuth.hasAnyScope(requiredScopes)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "API key missing required scope. Required: " + 
                    java.util.Arrays.toString(requiredScopes) + 
                    ", Available: " + apiKeyAuth.getScopes()
            );
        }
    }

    public boolean hasAccessToOrganization(String organizationId) {
        User user = getCurrentUser();

        // Super admins have access to all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // FIX: Add null check to prevent NullPointerException
        if (user.getOrganizationId() == null) {
            return false;
        }

        // Users can only access their own organization
        return user.getOrganizationId().equals(organizationId);
    }

    public boolean hasAccessToEnvironment(String environmentId) {
        User user = getCurrentUser();

        // Super admins have access to all environments
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Check if the environment is in the user's accessible environments
        return user.getAccessibleEnvironmentIds().contains(environmentId);
    }

    public boolean hasAccessToCluster(String clusterId) {
        // Check API key scopes if authenticated via API key
        checkApiKeyScope("clusters:read", "clusters:write");
        
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String environmentId = getClusterEnvironmentIdCached(clusterId);
        return hasAccessToEnvironment(environmentId);
    }

    /**
     * Get environment ID for a cluster with caching to avoid repeated database queries.
     */
    private String getClusterEnvironmentIdCached(String clusterId) {
        Instant now = Instant.now();
        
        // Check cache first
        ClusterMappingCacheEntry cached = clusterMappingCache.get(clusterId);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.environmentId;
        }
        
        // Load from database
        String environmentId = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::getEnvironmentId)
                .orElseThrow(() -> new RuntimeException("Cluster not found for auth check"));
        
        // Cache the mapping
        clusterMappingCache.put(clusterId, new ClusterMappingCacheEntry(environmentId, 
                now.plusSeconds(CLUSTER_MAPPING_CACHE_TTL_SECONDS)));
        
        // Periodic cleanup of expired entries (when cache size exceeds threshold)
        if (clusterMappingCache.size() > 1000) {
            cleanupExpiredClusterMappingCacheEntries(now);
        }
        
        return environmentId;
    }

    /**
     * Clean up expired cluster mapping cache entries.
     */
    private void cleanupExpiredClusterMappingCacheEntries(Instant now) {
        clusterMappingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    public boolean hasAccessToSchemaRegistry(String schemaRegistryId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = getSchemaRegistryClusterIdCached(schemaRegistryId);
        return hasAccessToCluster(clusterId);
    }

    /**
     * Get cluster ID for a schema registry with caching to avoid repeated database queries.
     */
    private String getSchemaRegistryClusterIdCached(String schemaRegistryId) {
        return getResourceClusterIdCached("schemaRegistry", schemaRegistryId, 
                () -> schemaRegistryRepository.findById(schemaRegistryId)
                        .map(SchemaRegistryEntity::getClusterId)
                        .orElseThrow(() -> new RuntimeException("Schema Registry not found for auth check")));
    }

    public boolean hasAccessToKafkaConnect(String kafkaConnectId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = getKafkaConnectClusterIdCached(kafkaConnectId);
        return hasAccessToCluster(clusterId);
    }

    /**
     * Get cluster ID for a Kafka Connect instance with caching to avoid repeated database queries.
     */
    private String getKafkaConnectClusterIdCached(String kafkaConnectId) {
        return getResourceClusterIdCached("kafkaConnect", kafkaConnectId,
                () -> kafkaConnectRepository.findById(kafkaConnectId)
                        .map(KafkaConnectEntity::getClusterId)
                        .orElseThrow(() -> new RuntimeException("Kafka Connect not found for auth check")));
    }

    public boolean hasAccessToKafkaStreamsApp(String kafkaStreamsApplicationId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = getKafkaStreamsAppClusterIdCached(kafkaStreamsApplicationId);
        return hasAccessToCluster(clusterId);
    }

    /**
     * Get cluster ID for a Kafka Streams application with caching to avoid repeated database queries.
     */
    private String getKafkaStreamsAppClusterIdCached(String kafkaStreamsApplicationId) {
        return getResourceClusterIdCached("kafkaStreamsApp", kafkaStreamsApplicationId,
                () -> kafkaStreamsApplicationRepository.findById(kafkaStreamsApplicationId)
                        .map(KafkaStreamsApplicationEntity::getClusterId)
                        .orElseThrow(() -> new RuntimeException("Kafka Streams Application not found for auth check")));
    }

    public boolean hasAccessToKsqlDB(String ksqlDBId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = getKsqlDBClusterIdCached(ksqlDBId);
        return hasAccessToCluster(clusterId);
    }

    /**
     * Get cluster ID for a ksqlDB instance with caching to avoid repeated database queries.
     */
    private String getKsqlDBClusterIdCached(String ksqlDBId) {
        return getResourceClusterIdCached("ksqlDB", ksqlDBId,
                () -> ksqlDBRepository.findById(ksqlDBId)
                        .map(KsqlDBEntity::getClusterId)
                        .orElseThrow(() -> new RuntimeException("ksqlDB instance not found for auth check")));
    }

    /**
     * Generic method to get cluster ID for a resource with caching.
     * Uses a prefix to avoid cache key collisions between different resource types.
     */
    private String getResourceClusterIdCached(String resourceType, String resourceId, 
                                               java.util.function.Supplier<String> loader) {
        String cacheKey = resourceType + ":" + resourceId;
        Instant now = Instant.now();
        
        // Check cache first
        ResourceMappingCacheEntry cached = resourceMappingCache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.clusterId;
        }
        
        // Load from database
        String clusterId = loader.get();
        
        // Cache the mapping
        resourceMappingCache.put(cacheKey, new ResourceMappingCacheEntry(clusterId, 
                now.plusSeconds(RESOURCE_MAPPING_CACHE_TTL_SECONDS)));
        
        // Periodic cleanup of expired entries (when cache size exceeds threshold)
        if (resourceMappingCache.size() > 1000) {
            cleanupExpiredResourceMappingCacheEntries(now);
        }
        
        return clusterId;
    }

    /**
     * Clean up expired resource mapping cache entries.
     */
    private void cleanupExpiredResourceMappingCacheEntries(Instant now) {
        resourceMappingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }


    public boolean canManageTopics() {
        // Check API key scopes if authenticated via API key
        checkApiKeyScope("topics:write");
        
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }
    
    /**
     * Check if user can read topics (for API keys with topics:read scope).
     */
    public boolean canReadTopics() {
        // Check API key scopes if authenticated via API key
        checkApiKeyScope("topics:read");
        
        // For JWT, any authenticated user can read topics
        return true;
    }

    public boolean canManageUsers() {
        User user = getCurrentUser();
        return user.getRole() == Role.SERVER_ADMIN 
            || user.getRole() == Role.SUPER_ADMIN
            || user.getRole() == Role.ADMIN;
    }

    /**
     * Check if user can manage users in a specific organization.
     * ADMIN can only manage users in their own organization.
     * SERVER_ADMIN and SUPER_ADMIN can manage users in any organization.
     */
    public boolean canManageUsersInOrganization(String organizationId) {
        User user = getCurrentUser();
        
        // SUPER_ADMIN and SERVER_ADMIN can manage all users
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.SERVER_ADMIN) {
            return true;
        }
        
        // ADMIN can manage users in their own organization
        if (user.getRole() == Role.ADMIN) {
            return hasAccessToOrganization(organizationId);
        }
        
        return false;
    }

    /**
     * Check if user can manage their own organization (view and update, not create/delete).
     * ADMIN can manage their own organization.
     * SUPER_ADMIN can manage all organizations.
     */
    public boolean canManageOwnOrganization(String organizationId) {
        User user = getCurrentUser();
        
        // SUPER_ADMIN can manage all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        
        // ADMIN can manage their own organization
        if (user.getRole() == Role.ADMIN) {
            return hasAccessToOrganization(organizationId);
        }
        
        return false;
    }

    public boolean canManageOrganizations() {
        User user = getCurrentUser();
        return user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canAccessProdEnvironment() {
        User user = getCurrentUser();

        // Super admins can access prod
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Admins can access prod
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        // Viewers cannot access prod
        return false;
    }

    /**
     * Check if current user is in MFA grace period (has grace period token).
     * Users in grace period can only perform MFA setup operations.
     * 
     * @param request Optional HttpServletRequest. If not provided, will attempt to extract from RequestContextHolder.
     *                This allows the method to work in contexts where RequestContextHolder may not be available
     *                (e.g., async, scheduled tasks).
     * @return true if user is in grace period, false otherwise
     */
    public boolean isInGracePeriod(HttpServletRequest request) {
        try {
            // If request not provided, try to get from RequestContextHolder
            if (request == null) {
                try {
                    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attributes != null) {
                        request = attributes.getRequest();
                    }
                } catch (Exception e) {
                    // RequestContextHolder not available (e.g., async context)
                    log.debug("RequestContextHolder not available for grace period check: {}", e.getMessage());
                    return false;
                }
            }

            if (request == null) {
                return false;
            }

            // Check cookie
            String jwt = null;
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                    if ("brokr_token".equals(cookie.getName())) {
                        jwt = cookie.getValue();
                        break;
                    }
                }
            }

            // Check Authorization header
            if (jwt == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    jwt = authHeader.substring(7);
                }
            }

            if (jwt != null) {
                return jwtService.isGracePeriodToken(jwt);
            }
        } catch (Exception e) {
            // If we can't determine, assume not in grace period
            log.debug("Error checking grace period: {}", e.getMessage());
        }
        return false;
    }


    /**
     * Check if user needs to complete MFA setup (in grace period or MFA not enabled when required).
     * Throws exception if user tries to access non-MFA operations while in grace period.
     */
    public void checkMfaSetupRequired(HttpServletRequest request) {
        if (isInGracePeriod(request)) {
            throw new RuntimeException("MFA setup is required. Please complete MFA setup to access this feature.");
        }
    }
}