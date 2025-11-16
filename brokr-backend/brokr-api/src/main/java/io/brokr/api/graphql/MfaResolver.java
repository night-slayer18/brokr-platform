package io.brokr.api.graphql;

import io.brokr.api.service.AuditService;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.JwtService;
import io.brokr.security.service.MfaService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MfaResolver {

    private final MfaService mfaService;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final AuditService auditService;

    private HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private boolean isSecureRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) return false;
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> mfaStatus() {
        String userId = authorizationService.getCurrentUser().getId();
        MfaService.MfaStatus status = mfaService.getMfaStatus(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", status.isEnabled());
        result.put("type", status.getType() != null ? status.getType().name() : null);
        result.put("unusedBackupCodesCount", status.getUnusedBackupCodesCount());
        return result;
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> setupMfa() {
        String userId = authorizationService.getCurrentUser().getId();
        User user = authorizationService.getCurrentUser();
        
        MfaService.MfaSetupResult result = mfaService.initiateMfaSetup(userId);
        
        // Log audit event
        try {
            auditService.logMfaSetupInitiated(userId, user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to log MFA setup initiated audit event: {}", e.getMessage());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("secretKey", result.getSecretKey());
        response.put("qrCodeDataUrl", result.getQrCodeDataUrl());
        response.put("qrCodeUri", result.getQrCodeUri());
        response.put("deviceId", result.getDeviceId());
        return response;
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> verifyMfaSetup(@Argument String deviceId, @Argument String code) {
        String userId = authorizationService.getCurrentUser().getId();
        User user = authorizationService.getCurrentUser();
        boolean wasInGracePeriod = authorizationService.isInGracePeriod();
        
        MfaService.MfaSetupCompleteResult result = mfaService.verifyAndCompleteMfaSetup(userId, deviceId, code);
        
        // Log audit event
        try {
            auditService.logMfaSetupCompleted(userId, user.getEmail());
        } catch (Exception e) {
            log.warn("Failed to log MFA setup completed audit event: {}", e.getMessage());
        }
        
        // If user was in grace period, issue a full token now that MFA is enabled
        if (wasInGracePeriod) {
            // Reload user to get updated MFA status (MFA is now enabled)
            user = authorizationService.getCurrentUser();
            
            // Generate full JWT token (MFA is now enabled)
            String fullToken = jwtService.generateToken(user);
            
            // Update cookie with full token
            HttpServletResponse response = getResponse();
            if (response != null) {
                Cookie cookie = new Cookie("brokr_token", fullToken);
                cookie.setHttpOnly(true);
                cookie.setSecure(isSecureRequest());
                cookie.setPath("/");
                cookie.setMaxAge(86400); // 24 hours
                response.addCookie(cookie);
            }
            
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("backupCodes", result.getBackupCodes());
            responseMap.put("token", fullToken); // Return new token for frontend
            responseMap.put("mfaGracePeriodCompleted", true);
            return responseMap;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("backupCodes", result.getBackupCodes());
        return response;
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean disableMfa(@Argument String password) {
        try {
            String userId = authorizationService.getCurrentUser().getId();
            User user = authorizationService.getCurrentUser();
            
            // Verify password before disabling MFA
            if (!authenticationService.verifyPassword(user.getEmail(), password)) {
                throw new RuntimeException("Invalid password");
            }
            
            // Disable MFA
            mfaService.disableMfa(userId);
            
            // Log audit event
            try {
                auditService.logMfaDisabled(userId, user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to log MFA disabled audit event: {}", e.getMessage());
            }
            
            return true;
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to disable MFA: " + e.getMessage(), e);
        }
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public List<String> regenerateBackupCodes(@Argument String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("Password is required");
        }
        
        try {
            String userId = authorizationService.getCurrentUser().getId();
            User user = authorizationService.getCurrentUser();
            
            // Verify password before regenerating backup codes
            boolean passwordValid = authenticationService.verifyPassword(user.getEmail(), password);
            if (!passwordValid) {
                throw new RuntimeException("Invalid password. Please check your password and try again.");
            }
            
            List<String> backupCodes = mfaService.regenerateBackupCodes(userId);
            
            // Log audit event (non-blocking)
            try {
                auditService.logMfaBackupCodesRegenerated(userId, user.getEmail());
            } catch (Exception e) {
                log.warn("Failed to log MFA backup codes regenerated audit event: {}", e.getMessage());
            }
            
            return backupCodes;
        } catch (RuntimeException e) {
            // Re-throw with clear message
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to regenerate backup codes: " + e.getMessage(), e);
        }
    }
}

