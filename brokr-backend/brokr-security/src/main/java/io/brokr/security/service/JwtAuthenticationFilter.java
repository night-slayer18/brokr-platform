package io.brokr.security.service;

import io.brokr.security.model.ApiKeyAuthenticationToken;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final long USER_CACHE_TTL_SECONDS = 300; // 5 minutes
    
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    
    // Cache for user details: email -> (userDetails, expirationTime)
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
        String jwt = null;
        String userEmail = null;

        // First, try to get token from HttpOnly cookie (preferred, more secure)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("brokr_token".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        // Fallback to Authorization header for backward compatibility (e.g., API clients)
        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null) {
                // Check if API key authentication is already set - don't override it
                Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
                if (existingAuth instanceof ApiKeyAuthenticationToken) {
                    // API key authentication already set, skip JWT processing
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // Process JWT token - always set authentication if valid (overrides anonymous/null)
                // Use cached user details to avoid database query on every request
                UserDetails userDetails = loadUserDetailsCached(userEmail);

                if (jwtService.validateToken(jwt, userDetails)) {
                    // Reject challenge tokens - only accept fully verified tokens
                    if (jwtService.isChallengeToken(jwt)) {
                        // Don't set authentication - user needs to complete MFA
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Create authenticated token (3-parameter constructor with authorities automatically marks it as authenticated)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (SignatureException e) {
            // Invalid token signature - security issue, log for monitoring
            log.warn("Invalid JWT signature for token from IP: {}", getClientIpAddress(request));
        } catch (ExpiredJwtException e) {
            // Token expired - log for monitoring (may indicate token refresh issues)
            log.debug("Expired JWT token for user: {}", e.getClaims().getSubject());
        } catch (JwtException e) {
            // Other JWT exceptions - log for monitoring
            log.warn("JWT validation failed: {}", e.getMessage());
        } catch (Exception e) {
            // Unexpected error - log for debugging
            log.error("Unexpected error processing JWT authentication", e);
        }

        filterChain.doFilter(request, response);
    }
    
    /**
     * Load user details with caching to avoid database queries on every JWT request.
     * Cache TTL is 5 minutes to balance performance and data freshness.
     */
    private UserDetails loadUserDetailsCached(String email) {
        long now = System.currentTimeMillis();
        
        // Check cache
        CacheEntry entry = userCache.get(email);
        if (entry != null && !entry.isExpired()) {
            return entry.userDetails;
        }
        
        // Cache miss or expired - load from database
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        
        // Update cache
        long expirationTime = now + TimeUnit.SECONDS.toMillis(USER_CACHE_TTL_SECONDS);
        userCache.put(email, new CacheEntry(userDetails, expirationTime));
        
        // Periodic cleanup of expired entries (simple approach - clean on cache miss)
        if (userCache.size() > 1000) {
            userCache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        
        return userDetails;
    }
    
    /**
     * Get client IP address from request for logging purposes.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Handle comma-separated IPs (X-Forwarded-For can have multiple)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip != null ? ip : "unknown";
    }
}