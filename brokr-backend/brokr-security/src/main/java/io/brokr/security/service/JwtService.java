package io.brokr.security.service;

import io.brokr.core.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret:mySecretKey}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private long expiration;

    @Value("${jwt.challenge-expiration:300}") // 5 minutes for MFA challenge token
    private long challengeExpiration;

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

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        // Use email as subject for authentication (email is unique and used for login)
        claims.put("sub", user.getEmail());
        claims.put("roles", user.getRole().name());
        claims.put("organizationId", user.getOrganizationId());
        claims.put("accessibleEnvironmentIds", user.getAccessibleEnvironmentIds());
        claims.put("mfaVerified", true); // Full token means MFA is verified

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