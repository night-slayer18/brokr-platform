package io.brokr.security.service;

import io.brokr.core.model.User;
import io.brokr.security.model.BrokrUserDetails;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserDetailsService implementation with caching to reduce database queries.
 * Cache is invalidated when users are updated to ensure data consistency.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    // Cache for user details: email -> (userDetails, expirationTime)
    private final Map<String, CacheEntry> emailCache = new ConcurrentHashMap<>();
    // Cache for user details: userId -> (userDetails, expirationTime)
    private final Map<String, CacheEntry> userIdCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes

    private static class CacheEntry {
        final UserDetails userDetails;
        final Instant expiresAt;

        CacheEntry(UserDetails userDetails, Instant expiresAt) {
            this.userDetails = userDetails;
            this.expiresAt = expiresAt;
        }

        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Instant now = Instant.now();
        
        // Check cache first
        CacheEntry cached = emailCache.get(email);
        if (cached != null && !cached.isExpired(now)) {
            return cached.userDetails;
        }
        
        // Cache miss or expired - load from database
        // Note: Spring Security's loadUserByUsername method is used with email for authentication
        // Transaction is active here, so Hibernate.initialize() will work correctly
        User user = userRepository.findByEmail(email)
                .map(entity -> {
                    // Initialize lazy collection while transaction is active
                    Hibernate.initialize(entity.getAccessibleEnvironmentIds());
                    return entity.toDomain();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        UserDetails userDetails = new BrokrUserDetails(user);
        
        // Update cache
        Instant expirationTime = now.plusSeconds(CACHE_TTL_SECONDS);
        emailCache.put(email, new CacheEntry(userDetails, expirationTime));
        
        // Also cache by userId for loadUserById() to benefit from this lookup
        userIdCache.put(user.getId(), new CacheEntry(userDetails, expirationTime));
        
        // Periodic cleanup of expired entries (when cache size exceeds threshold)
        if (emailCache.size() > 1000) {
            cleanupExpiredEntries(now);
        }
        
        return userDetails;
    }

    /**
     * Load user details by user ID (UUID).
     * Used for API key authentication where we have the user ID but not the email.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        Instant now = Instant.now();
        
        // Check cache first
        CacheEntry cached = userIdCache.get(userId);
        if (cached != null && !cached.isExpired(now)) {
            return cached.userDetails;
        }
        
        // Cache miss or expired - load from database
        // Transaction is active here, so Hibernate.initialize() will work correctly
        User user = userRepository.findById(userId)
                .map(entity -> {
                    // Initialize lazy collection while transaction is active
                    Hibernate.initialize(entity.getAccessibleEnvironmentIds());
                    return entity.toDomain();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        UserDetails userDetails = new BrokrUserDetails(user);
        
        // Update cache
        Instant expirationTime = now.plusSeconds(CACHE_TTL_SECONDS);
        userIdCache.put(userId, new CacheEntry(userDetails, expirationTime));
        
        // Also cache by email for loadUserByUsername() to benefit from this lookup
        emailCache.put(user.getEmail(), new CacheEntry(userDetails, expirationTime));
        
        // Periodic cleanup of expired entries (when cache size exceeds threshold)
        if (userIdCache.size() > 1000) {
            cleanupExpiredEntries(now);
        }
        
        return userDetails;
    }

    /**
     * Evict cache entries for a user by email.
     * Should be called when user data is updated to ensure cache consistency.
     */
    public void evictCacheByEmail(String email) {
        emailCache.remove(email);
    }

    /**
     * Evict cache entries for a user by ID.
     * Should be called when user data is updated to ensure cache consistency.
     */
    public void evictCacheById(String userId) {
        userIdCache.remove(userId);
    }

    /**
     * Evict all cache entries for a user (by both email and ID).
     * Should be called when user data is updated to ensure cache consistency.
     */
    public void evictCacheForUser(String email, String userId) {
        emailCache.remove(email);
        userIdCache.remove(userId);
    }

    /**
     * Clean up expired cache entries.
     */
    private void cleanupExpiredEntries(Instant now) {
        emailCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        userIdCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}