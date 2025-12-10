package io.brokr.api.graphql;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.api.service.AuditService;
import io.brokr.core.dto.UserDto;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import io.brokr.security.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthResolver {

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

    @MutationMapping
    public Map<String, Object> login(@Argument LoginInput input) {
        Map<String, Object> authResult = authenticationService.authenticate(input.getUsername(), input.getPassword());
        HttpServletResponse response = getResponse();
        
        Boolean mfaRequired = (Boolean) authResult.getOrDefault("mfaRequired", false);
        Boolean mfaGracePeriod = (Boolean) authResult.getOrDefault("mfaGracePeriod", false);
        String token = (String) authResult.get("token");
        
        if (response != null && token != null) {
            // Set HttpOnly cookie with token using ResponseCookie for SameSite support
            ResponseCookie cookie = ResponseCookie.from("brokr_token", token)
                    .httpOnly(true)
                    .secure(isSecureRequest())
                    .path("/")
                    .maxAge(mfaRequired ? 300 : 86400)
                    .sameSite("Strict")
                    .build();
            
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        
        // Build response according to AuthResponse type
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", token != null ? token : ""); // GraphQL requires non-null
        responseBody.put("user", authResult.get("user")); // May be null if MFA required
        responseBody.put("mfaRequired", mfaRequired);
        responseBody.put("mfaType", authResult.get("mfaType"));
        responseBody.put("mfaGracePeriod", mfaGracePeriod);
        responseBody.put("mfaGracePeriodDaysRemaining", authResult.get("mfaGracePeriodDaysRemaining"));
        responseBody.put("mfaSetupRequired", authResult.get("mfaSetupRequired"));
        return responseBody;
    }

    @MutationMapping
    public Map<String, Object> verifyMfaCode(@Argument String challengeToken, @Argument String code, @Argument Boolean isBackupCode) {
        // Extract user info from challenge token for audit logging
        String userId = null;
        String userEmail = null;
        String method = (isBackupCode != null && isBackupCode) ? "BACKUP_CODE" : "TOTP";
        
        try {
            if (jwtService.isChallengeToken(challengeToken)) {
                userId = jwtService.extractUserIdFromChallengeToken(challengeToken);
                userEmail = jwtService.extractUsername(challengeToken);
            }
        } catch (Exception e) {
            // Ignore - will try to get user info from auth result instead
        }
        
        try {
            Map<String, Object> authResult = authenticationService.verifyMfaAndAuthenticate(
                    challengeToken, 
                    code, 
                    isBackupCode != null && isBackupCode
            );
            
            // Get user from result for audit logging
            if (authResult.get("user") != null) {
                UserDto userDto = (UserDto) authResult.get("user");
                userId = userDto.getId();
                userEmail = userDto.getEmail();
            }
            
            // Log successful verification
            if (userId != null && userEmail != null) {
                try {
                    auditService.logMfaVerification(userId, userEmail, true, method, null);
                } catch (Exception e) {
                    log.warn("Failed to log MFA verification success audit event: {}", e.getMessage());
                }
            }
            
            HttpServletResponse response = getResponse();
            
            if (response != null) {
                // Set HttpOnly cookie with full JWT token using ResponseCookie
                String token = (String) authResult.get("token");
                ResponseCookie cookie = ResponseCookie.from("brokr_token", token)
                        .httpOnly(true)
                        .secure(isSecureRequest())
                        .path("/")
                        .maxAge(86400) // 24 hours
                        .sameSite("Strict")
                        .build();
                
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            }
            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("token", authResult.get("token"));
            responseBody.put("user", authResult.get("user"));
            responseBody.put("mfaRequired", false);
            responseBody.put("mfaType", null);
            return responseBody;
        } catch (Exception e) {
            // Log failed verification
            if (userId != null && userEmail != null) {
                try {
                    auditService.logMfaVerification(userId, userEmail, false, method, e.getMessage());
                } catch (Exception auditEx) {
                    log.warn("Failed to log MFA verification failed audit event: {}", auditEx.getMessage());
                }
            }
            throw e;
        }
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Map<String, Object> register(@Argument UserInput input) {
        // Convert API Input DTO to Core Model
        User user = User.builder()
                .username(input.getUsername())
                .email(input.getEmail())
                .password(input.getPassword())
                .firstName(input.getFirstName())
                .lastName(input.getLastName())
                .role(input.getRole())
                .organizationId(input.getOrganizationId())
                .accessibleEnvironmentIds(input.getAccessibleEnvironmentIds())
                .isActive(input.getIsActive() != null ? input.getIsActive() : true) // UserManagementService will handle default
                .build();

        Map<String, Object> authResult = authenticationService.register(user);
        HttpServletResponse response = getResponse();
        
        if (response != null) {
            // Set HttpOnly cookie with JWT token using ResponseCookie
            String token = (String) authResult.get("token");
            ResponseCookie cookie = ResponseCookie.from("brokr_token", token)
                    .httpOnly(true)
                    .secure(isSecureRequest())
                    .path("/")
                    .maxAge(86400) // 24 hours
                    .sameSite("Strict")
                    .build();
            
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        
        // Remove token from response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
    }
}