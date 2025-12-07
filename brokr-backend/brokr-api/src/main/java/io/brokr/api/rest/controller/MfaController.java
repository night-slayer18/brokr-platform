package io.brokr.api.rest.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for MFA operations.
 * Thin wrapper around MfaService - no service changes needed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/brokr/mfa")
@RequiredArgsConstructor
public class MfaController {
    
    private final MfaService mfaService;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    
    private HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }
    
    private boolean isSecureRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) return false;
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
    
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getMfaStatus() {
        String userId = authorizationService.getCurrentUser().getId();
        MfaService.MfaStatus status = mfaService.getMfaStatus(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", status.isEnabled());
        result.put("type", status.getType() != null ? status.getType().name() : null);
        result.put("unusedBackupCodesCount", status.getUnusedBackupCodesCount());
        return result;
    }
    
    @PostMapping("/setup")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> setupMfa() {
        String userId = authorizationService.getCurrentUser().getId();
        
        MfaService.MfaSetupResult result = mfaService.initiateMfaSetup(userId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("secretKey", result.getSecretKey());
        response.put("qrCodeDataUrl", result.getQrCodeDataUrl());
        response.put("qrCodeUri", result.getQrCodeUri());
        response.put("deviceId", result.getDeviceId());
        return response;
    }
    
    @PostMapping("/verify-setup")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> verifyMfaSetup(
            @RequestParam String deviceId,
            @RequestParam String code,
            HttpServletRequest request) {
        String userId = authorizationService.getCurrentUser().getId();
        User user = authorizationService.getCurrentUser();
        boolean wasInGracePeriod = authorizationService.isInGracePeriod(request);
        
        MfaService.MfaSetupCompleteResult result = mfaService.verifyAndCompleteMfaSetup(userId, deviceId, code);
        
        // If user was in grace period, issue a full token now that MFA is enabled
        if (wasInGracePeriod) {
            user = authorizationService.getCurrentUser();
            // MFA was verified during setup, so mfaVerified is true
            String fullToken = jwtService.generateToken(user, true);
            
            HttpServletResponse response = getResponse();
            if (response != null) {
                Cookie cookie = new Cookie("brokr_token", fullToken);
                cookie.setHttpOnly(true);
                cookie.setSecure(isSecureRequest());
                cookie.setPath("/");
                cookie.setMaxAge(86400);
                response.addCookie(cookie);
            }
            
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("backupCodes", result.getBackupCodes());
            responseMap.put("token", fullToken);
            responseMap.put("mfaGracePeriodCompleted", true);
            return responseMap;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("backupCodes", result.getBackupCodes());
        return response;
    }
    
    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> disableMfa(@RequestParam String password) {
        String userId = authorizationService.getCurrentUser().getId();
        User user = authorizationService.getCurrentUser();
        
        if (!authenticationService.verifyPassword(user.getEmail(), password)) {
            throw new RuntimeException("Invalid password");
        }
        
        mfaService.disableMfa(userId);
        return org.springframework.http.ResponseEntity.ok(true);
    }
    
    @PostMapping("/backup-codes/regenerate")
    @PreAuthorize("isAuthenticated()")
    public List<String> regenerateBackupCodes(@RequestParam String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("Password is required");
        }
        
        String userId = authorizationService.getCurrentUser().getId();
        User user = authorizationService.getCurrentUser();
        
        boolean passwordValid = authenticationService.verifyPassword(user.getEmail(), password);
        if (!passwordValid) {
            throw new RuntimeException("Invalid password");
        }
        
        return mfaService.regenerateBackupCodes(userId);
    }
}

