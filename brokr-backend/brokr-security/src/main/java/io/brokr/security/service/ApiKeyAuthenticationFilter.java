package io.brokr.security.service;

import io.brokr.security.model.ApiKeyAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * API Key authentication filter.
 * Runs BEFORE JWT filter to handle API key authentication.
 * Thread-safe, no race conditions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String KEY_PREFIX = "brokr_";
    private static final String GENERIC_ERROR_MESSAGE = "Invalid API key";
    private static final long USER_CACHE_TTL_SECONDS = 300; // 5 minutes
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyUsageService usageService;
    private final ApiKeyRateLimitService rateLimitService;
    private final UserDetailsServiceImpl userDetailsService;
    
    // Cache for user details: userId -> (userDetails, expirationTime)
    private final ConcurrentHashMap<String, CacheEntry> userCache = new ConcurrentHashMap<>();
    
    private static class CacheEntry {
        final UserDetails userDetails;
        final long expirationTime;
        
        CacheEntry(UserDetails userDetails, long expirationTime) {
            this.userDetails = userDetails;
            this.expirationTime = expirationTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Only process if Authorization header exists
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No auth header, let JWT filter handle (might be cookie-based)
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = authHeader.substring(7);
        
        // Only process if it looks like an API key (starts with "brokr_")
        if (!token.startsWith(KEY_PREFIX)) {
            // Not an API key, let JWT filter handle it
            filterChain.doFilter(request, response);
            return;
        }
        
        // This is an API key, process it
        try {
            // Validate API key
            ApiKeyService.ApiKeyValidationResult validation = apiKeyService.validateApiKey(token);
            if (!validation.isValid()) {
                // Invalid API key - use generic error message to prevent information leakage
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        String.format("{\"error\":\"%s\"}", GENERIC_ERROR_MESSAGE)
                );
                return;
            }
            
            // Check rate limit (before processing request)
            if (!rateLimitService.checkRateLimit(validation.getApiKeyId(), request)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return;
            }
            
            // Check if authentication already set (prevent duplicate processing)
            Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
            if (existingAuth != null && existingAuth.isAuthenticated()) {
                // Authentication already set, continue
                filterChain.doFilter(request, response);
                return;
            }
            
            // Load user details by ID (cached to avoid database query on every request)
            UserDetails userDetails = loadUserDetailsCached(validation.getUserId());
            
            // Create API key authentication token
            // Note: Credentials (API key) are not stored in the token for security
            ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(
                    userDetails,
                    validation.getApiKeyId(),
                    validation.getScopes()
            );
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Record usage (async, don't block)
            usageService.recordUsageAsync(
                    validation.getApiKeyId(),
                    validation.getUserId(),
                    validation.getOrganizationId(),
                    request
            );
            
            // Update last used (async)
            apiKeyService.updateLastUsed(validation.getApiKeyId());
            
            log.debug("API key authenticated: {}", validation.getApiKeyId());
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Security-related exceptions (invalid key format, revoked key, etc.) - fail closed
            log.warn("Security exception in API key authentication: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    String.format("{\"error\":\"%s\"}", GENERIC_ERROR_MESSAGE)
            );
            return;
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            // User not found - security issue, fail closed
            log.warn("User not found for API key authentication: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    String.format("{\"error\":\"%s\"}", GENERIC_ERROR_MESSAGE)
            );
            return;
        } catch (Exception e) {
            // Other exceptions (database errors, etc.) - log and let JWT filter try
            log.error("Unexpected error processing API key authentication", e);
            filterChain.doFilter(request, response);
            return;
        }
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
    
    /**
     * Load user details with caching to avoid database queries on every API key request.
     * Cache TTL is 5 minutes to balance performance and data freshness.
     */
    private UserDetails loadUserDetailsCached(String userId) {
        long now = System.currentTimeMillis();
        
        // Check cache
        CacheEntry entry = userCache.get(userId);
        if (entry != null && !entry.isExpired()) {
            return entry.userDetails;
        }
        
        // Cache miss or expired - load from database
        UserDetails userDetails = userDetailsService.loadUserById(userId);
        
        // Update cache
        long expirationTime = now + TimeUnit.SECONDS.toMillis(USER_CACHE_TTL_SECONDS);
        userCache.put(userId, new CacheEntry(userDetails, expirationTime));
        
        // Periodic cleanup of expired entries (simple approach - clean on cache miss)
        if (userCache.size() > 1000) {
            userCache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        
        return userDetails;
    }
}

