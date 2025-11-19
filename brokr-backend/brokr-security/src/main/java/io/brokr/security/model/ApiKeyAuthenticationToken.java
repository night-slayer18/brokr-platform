package io.brokr.security.model;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

/**
 * Spring Security authentication token for API key authentication.
 * Extends AbstractAuthenticationToken to work with Spring Security framework.
 */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    
    private final UserDetails userDetails;
    private final String apiKeyId;
    private final Set<String> scopes;
    
    /**
     * Create authenticated API key token.
     * 
     * @param userDetails User details from UserDetailsService
     * @param apiKeyId API key ID
     * @param scopes Set of permission scopes
     */
    public ApiKeyAuthenticationToken(
            UserDetails userDetails,
            String apiKeyId,
            Set<String> scopes
    ) {
        super(userDetails.getAuthorities());
        this.userDetails = userDetails;
        this.apiKeyId = apiKeyId;
        this.scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
        setAuthenticated(true); // Mark as authenticated
    }
    
    @Override
    public Object getPrincipal() {
        return userDetails;
    }
    
    @Override
    public Object getCredentials() {
        // Security: Return null after authentication to prevent credential leakage
        // Credentials should not be stored in security context
        return null;
    }
    
    public String getApiKeyId() {
        return apiKeyId;
    }
    
    public Set<String> getScopes() {
        return scopes;
    }
    
    public UserDetails getUserDetails() {
        return userDetails;
    }
    
    /**
     * Check if the token has a specific scope.
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
    
    /**
     * Check if the token has any of the required scopes.
     */
    public boolean hasAnyScope(String... requiredScopes) {
        for (String requiredScope : requiredScopes) {
            if (scopes.contains(requiredScope)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if the token has all of the required scopes.
     */
    public boolean hasAllScopes(String... requiredScopes) {
        for (String requiredScope : requiredScopes) {
            if (!scopes.contains(requiredScope)) {
                return false;
            }
        }
        return true;
    }
}

