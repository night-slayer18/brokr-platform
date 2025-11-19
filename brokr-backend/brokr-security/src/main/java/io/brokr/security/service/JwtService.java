package io.brokr.security.service;

import io.brokr.core.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private long expiration;

    @Value("${jwt.challenge-expiration:300}") // 5 minutes for MFA challenge token
    private long challengeExpiration;
    
    /**
     * Validates JWT secret configuration on application startup.
     * CRITICAL SECURITY: Prevents using default or weak secrets.
     */
    @PostConstruct
    public void validateJwtSecret() {
        // List of forbidden weak secrets
        String[] forbiddenSecrets = {
            "mySecretKey",
            "secret",
            "changeme",
            "password",
            "test",
            "dev",
            "localhost"
        };
        
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: JWT secret (jwt.secret) is not configured. " +
                "Application cannot start without a valid JWT secret. " +
                "Set jwt.secret in application.yml or via environment variable."
            );
        }
        
        // Check if using forbidden weak secrets
        String trimmedSecret = secret.trim();
        for (String forbidden : forbiddenSecrets) {
            if (trimmedSecret.equalsIgnoreCase(forbidden)) {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: JWT secret is set to a forbidden weak value: '" + forbidden + "'. " +
                    "This is a severe security vulnerability that allows authentication bypass. " +
                    "Generate a strong secret using: openssl rand -base64 64"
                );
            }
        }
        
        // Validate secret length (should be at least 256 bits = 32 bytes for HMAC-SHA256)
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(trimmedSecret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: JWT secret is not valid Base64. " +
                "The secret must be Base64-encoded. " +
                "Generate a valid secret using: openssl rand -base64 64"
            );
        }
        
        if (decodedKey.length < 32) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: JWT secret is too short (" + decodedKey.length + " bytes). " +
                "HMAC-SHA256 requires at least 256 bits (32 bytes) for security. " +
                "Current secret provides only " + (decodedKey.length * 8) + " bits. " +
                "Generate a secure secret using: openssl rand -base64 64"
            );
        }
        
        // Warn if secret is less than recommended 512 bits
        if (decodedKey.length < 64) {
            log.warn(
                "WARNING: JWT secret is only {} bytes ({} bits). " +
                "Recommend at least 512 bits (64 bytes) for enhanced security. " +
                "Generate with: openssl rand -base64 64",
                decodedKey.length, decodedKey.length * 8
            );
        }
        
        log.info("JWT secret validation passed: {} bytes ({} bits)", 
                decodedKey.length, decodedKey.length * 8);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Generate a full JWT token for an authenticated user.
     * 
     * @param user The authenticated user
     * @param mfaVerified Whether MFA was verified (true if MFA was verified, or if MFA is not required/enabled)
     * @return JWT token string
     */
    public String generateToken(User user, boolean mfaVerified) {
        Map<String, Object> claims = new HashMap<>();
        // Use email as subject for authentication (email is unique and used for login)
        claims.put("sub", user.getEmail());
        claims.put("roles", user.getRole().name());
        claims.put("organizationId", user.getOrganizationId());
        claims.put("accessibleEnvironmentIds", user.getAccessibleEnvironmentIds());
        claims.put("mfaVerified", mfaVerified);

        return createToken(claims, user.getEmail());
    }

    /**
     * Generate a challenge token for MFA verification (short-lived)
     */
    public String generateChallengeToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("mfaChallenge", true); // Indicates this is a challenge token
        claims.put("mfaVerified", false);

        return createChallengeToken(claims, user.getEmail());
    }

    /**
     * Generate a grace period token for users in MFA grace period (allows MFA setup only)
     */
    public String generateGracePeriodToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("userId", user.getId());
        claims.put("roles", user.getRole().name());
        claims.put("organizationId", user.getOrganizationId());
        claims.put("accessibleEnvironmentIds", user.getAccessibleEnvironmentIds());
        claims.put("mfaGracePeriod", true); // Indicates this is a grace period token
        claims.put("mfaVerified", false); // MFA not verified yet

        // Grace period tokens have longer expiration (24 hours) to allow time for MFA setup
        return createToken(claims, user.getEmail());
    }

    /**
     * Check if token is a grace period token
     */
    public boolean isGracePeriodToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Boolean mfaGracePeriod = claims.get("mfaGracePeriod", Boolean.class);
            return mfaGracePeriod != null && mfaGracePeriod;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if token is a challenge token
     */
    public boolean isChallengeToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Boolean mfaChallenge = claims.get("mfaChallenge", Boolean.class);
            return mfaChallenge != null && mfaChallenge;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract user ID from challenge token
     */
    public String extractUserIdFromChallengeToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", String.class);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSignInKey())
                .compact();
    }

    private String createChallengeToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + challengeExpiration * 1000))
                .signWith(getSignInKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        // extractUsername actually extracts email from token subject
        final String email = extractUsername(token);
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }


    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

}