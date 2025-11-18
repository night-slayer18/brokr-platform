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
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyUsageService usageService;
    private final ApiKeyRateLimitService rateLimitService;
    private final UserDetailsServiceImpl userDetailsService;
    
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
                // Invalid API key, return 401
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        String.format("{\"error\":\"%s\"}", validation.getErrorMessage())
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
            
            // Load user details by ID (API key validation returns userId, not email)
            UserDetails userDetails = userDetailsService.loadUserById(validation.getUserId());
            
            // Create API key authentication token
            ApiKeyAuthenticationToken authentication = new ApiKeyAuthenticationToken(
                    userDetails,
                    validation.getApiKeyId(),
                    validation.getScopes(),
                    token // Store for logging (not used for validation)
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
            
        } catch (Exception e) {
            // Log error but don't break the flow
            log.error("Error processing API key authentication", e);
            // Let JWT filter try (might be a JWT token that looks like API key)
            filterChain.doFilter(request, response);
            return;
        }
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
}

