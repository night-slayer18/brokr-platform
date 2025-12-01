package io.brokr.api.rest.controller;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.api.service.AuditService;
import io.brokr.core.dto.UserDto;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginInput input, HttpServletRequest request, HttpServletResponse response) {
        try {
            Map<String, Object> authResult = authenticationService.authenticate(input.getUsername(), input.getPassword());
            
            // Set HttpOnly cookie with JWT token (secure against XSS)
            String token = (String) authResult.get("token");
            Cookie cookie = new Cookie("brokr_token", token);
            cookie.setHttpOnly(true);
            // Set Secure flag based on whether request is HTTPS (production) or HTTP (dev)
            cookie.setSecure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 24 hours (matches JWT expiration)
            // Don't set domain - let it default to the request's domain
            // This allows cookies to work with Vite proxy (localhost:3000) and production
            response.addCookie(cookie);
            
            // Log successful login
            UserDto user = (UserDto) authResult.get("user");
            if (user != null) {
                auditService.logAuthentication("login", user.getId(), user.getEmail(), true, null);
            }
            
            // Remove token from response body for security
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("user", authResult.get("user"));
            return responseBody;
        } catch (Exception e) {
            // Log failed login attempt
            auditService.logAuthentication("login", null, input.getUsername(), false, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        // Clear the cookie
        Cookie cookie = new Cookie("brokr_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        cookie.setPath("/");
        cookie.setMaxAge(0); // Delete the cookie
        response.addCookie(cookie);
        
        // Log logout (try to get user info, but don't fail if not available)
        try {
            // User info should be available from security context
            // This will be handled by the audit service's automatic user context extraction
            auditService.logLogout(null, null); // Will be enriched by audit service
        } catch (Exception e) {
            // Don't fail logout if audit logging fails
        }
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");
        return result;
    }

    /**
     * Validates if the current JWT session is still valid.
     * This is a lightweight endpoint used by the frontend to check token expiry on app load.
     * Prevents split-brain issue where localStorage shows user as logged in but JWT has expired.
     * 
     * @return 200 OK if session valid, 401 Unauthorized if expired/invalid
     */
    @GetMapping("/validate")
    public Map<String, Object> validateSession() {
        // If this endpoint is reached, the JWT filter has already validated the token
        // and set the authentication in SecurityContext. Just return success.
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        return result;
    }

    @PostMapping("/register")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Map<String, Object> register(@RequestBody UserInput input, HttpServletRequest request, HttpServletResponse response) {
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
        
        // Set HttpOnly cookie with JWT token
        String token = (String) authResult.get("token");
        Cookie cookie = new Cookie("brokr_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24 hours
        response.addCookie(cookie);
        
        // Remove token from response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
    }
}