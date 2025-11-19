package io.brokr.security.service;

import io.brokr.core.dto.UserDto;
import io.brokr.core.model.User;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final long ORGANIZATION_CACHE_TTL_SECONDS = 300; // 5 minutes
    
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserManagementService userManagementService;
    private final MfaService mfaService;
    
    // Cache for organization MFA settings: organizationId -> (organization, expirationTime)
    private final ConcurrentHashMap<String, OrganizationCacheEntry> organizationCache = new ConcurrentHashMap<>();
    
    private static class OrganizationCacheEntry {
        final OrganizationEntity organization;
        final long expirationTime;
        
        OrganizationCacheEntry(OrganizationEntity organization, long expirationTime) {
            this.organization = organization;
            this.expirationTime = expirationTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }

    public Map<String, Object> register(User user) {
        // Delegate user creation and validation to the centralized UserManagementService
        User savedUser = userManagementService.createUser(user);

        // After successful creation, generate a token
        // New users don't have MFA enabled yet, so mfaVerified is true (no MFA required)
        String jwtToken = jwtService.generateToken(savedUser, true);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(savedUser));

        return response;
    }

    public Map<String, Object> authenticate(String email, String password) {
        // Note: The parameter is named 'email' but API field remains 'username' for backward compatibility
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        User user = userRepository.findByEmail(email)
                .map(UserEntity::toDomain)
                .orElseThrow();

        // Check organization MFA policy with grace period support
        if (user.getOrganizationId() != null) {
            // Use cached organization lookup to avoid database query on every authentication
            OrganizationEntity org = getOrganizationCached(user.getOrganizationId());
            if (org != null && org.isMfaRequired() && !user.isMfaEnabled()) {
                // Check if user is within grace period
                java.time.LocalDateTime mfaRequiredSince = org.getMfaRequiredSince();
                Integer gracePeriodDays = org.getMfaGracePeriodDays() != null ? org.getMfaGracePeriodDays() : 7;
                
                if (mfaRequiredSince != null) {
                    java.time.LocalDateTime gracePeriodEnd = mfaRequiredSince.plusDays(gracePeriodDays);
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    
                    if (now.isBefore(gracePeriodEnd)) {
                        // User is within grace period - allow login but require MFA setup
                        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, gracePeriodEnd);
                        
                        // Generate grace period token (allows MFA setup but restricts other operations)
                        String gracePeriodToken = jwtService.generateGracePeriodToken(user);
                        
                        Map<String, Object> response = new HashMap<>();
                        response.put("token", gracePeriodToken);
                        response.put("user", UserDto.fromDomain(user));
                        response.put("mfaRequired", false); // Not required for login, but required for full access
                        response.put("mfaGracePeriod", true);
                        response.put("mfaGracePeriodDaysRemaining", daysRemaining);
                        response.put("mfaSetupRequired", true); // Frontend should prompt for MFA setup
                        return response;
                    } else {
                        // Grace period has expired - block access
                        log.warn("User {} attempted login but MFA grace period has expired for organization {}", 
                                user.getEmail(), user.getOrganizationId());
                        throw new RuntimeException("MFA is required by your organization. The grace period has expired. Please contact your administrator.");
                    }
                } else {
                    // No timestamp set (legacy data) - set it now and give full grace period
                    log.warn("Organization {} has MFA required but no timestamp set. Setting timestamp now.", 
                            user.getOrganizationId());
                    org.setMfaRequiredSince(java.time.LocalDateTime.now());
                    organizationRepository.save(org);
                    // Invalidate cache after update to ensure fresh data
                    organizationCache.remove(user.getOrganizationId());
                    
                    // Allow login with grace period
                    String gracePeriodToken = jwtService.generateGracePeriodToken(user);
                    Map<String, Object> response = new HashMap<>();
                    response.put("token", gracePeriodToken);
                    response.put("user", UserDto.fromDomain(user));
                    response.put("mfaRequired", false);
                    response.put("mfaGracePeriod", true);
                    response.put("mfaGracePeriodDaysRemaining", gracePeriodDays);
                    response.put("mfaSetupRequired", true);
                    return response;
                }
            }
        }

        // Check if MFA is enabled
        if (user.isMfaEnabled()) {
            // Generate challenge token instead of full JWT
            String challengeToken = jwtService.generateChallengeToken(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", challengeToken);
            response.put("mfaRequired", true);
            response.put("mfaType", user.getMfaType().name());
            // Don't return user data yet - wait for MFA verification
            return response;
        }

        // No MFA - generate full JWT token
        // MFA is not enabled, so mfaVerified is true (no MFA required)
        String jwtToken = jwtService.generateToken(user, true);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(user));
        response.put("mfaRequired", false);

        return response;
    }

    /**
     * Verify MFA code and generate full JWT token
     */
    public Map<String, Object> verifyMfaAndAuthenticate(String challengeToken, String mfaCode, boolean isBackupCode) {
        // Validate challenge token
        if (!jwtService.isChallengeToken(challengeToken)) {
            throw new RuntimeException("Invalid challenge token");
        }

        String userId = jwtService.extractUserIdFromChallengeToken(challengeToken);
        User user = userRepository.findById(userId)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify MFA code
        // Use generic error message to prevent information leakage about rate limiting vs invalid code
        try {
            boolean isValid;
            if (isBackupCode) {
                isValid = mfaService.verifyBackupCode(userId, mfaCode);
            } else {
                isValid = mfaService.verifyMfaCode(userId, mfaCode);
            }

            if (!isValid) {
                // Use generic message - don't reveal whether it's invalid code or rate limiting
                throw new RuntimeException("Authentication failed");
            }
        } catch (RuntimeException e) {
            // Catch rate limiting exceptions and other MFA errors
            // Use generic message to prevent information leakage
            if (e.getMessage() != null && e.getMessage().contains("Too many failed attempts")) {
                // Rate limiting - use generic message
                throw new RuntimeException("Authentication failed");
            }
            // Re-throw other exceptions (like "No active MFA device found")
            throw e;
        }

        // Generate full JWT token - MFA was verified, so mfaVerified is true
        String jwtToken = jwtService.generateToken(user, true);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(user));
        response.put("mfaVerified", true);

        return response;
    }

    /**
     * Verify password for a user without going through full authentication flow.
     * This is used for operations like disabling MFA where we just need to verify the password.
     * 
     * @param email User's email
     * @param password Password to verify
     * @return true if password is correct, false otherwise
     */
    public boolean verifyPassword(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        
        try {
            UserEntity userEntity = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Check if user account is active
            if (!userEntity.isActive()) {
                return false;
            }
            
            // Check if user has a password set
            if (userEntity.getPassword() == null || userEntity.getPassword().trim().isEmpty()) {
                return false;
            }
            
            // Verify password using PasswordEncoder
            return passwordEncoder.matches(password, userEntity.getPassword());
        } catch (RuntimeException e) {
            // Re-throw RuntimeException (like "User not found")
            throw e;
        } catch (Exception e) {
            log.error("Password verification failed with exception for user: {}", email, e);
            return false;
        }
    }
    
    /**
     * Get organization with caching to avoid database queries on every authentication.
     * Cache TTL is 5 minutes to balance performance and data freshness.
     */
    private OrganizationEntity getOrganizationCached(String organizationId) {
        long now = System.currentTimeMillis();
        
        // Check cache
        OrganizationCacheEntry entry = organizationCache.get(organizationId);
        if (entry != null && !entry.isExpired()) {
            return entry.organization;
        }
        
        // Cache miss or expired - load from database
        OrganizationEntity organization = organizationRepository.findById(organizationId).orElse(null);
        
        // Update cache (even if null, to avoid repeated lookups for non-existent orgs)
        if (organization != null) {
            long expirationTime = now + TimeUnit.SECONDS.toMillis(ORGANIZATION_CACHE_TTL_SECONDS);
            organizationCache.put(organizationId, new OrganizationCacheEntry(organization, expirationTime));
        }
        
        // Periodic cleanup of expired entries
        if (organizationCache.size() > 1000) {
            organizationCache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        
        return organization;
    }
}