package io.brokr.api.rest.controller;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginInput input, HttpServletResponse response) {
        Map<String, Object> authResult = authenticationService.authenticate(input.getUsername(), input.getPassword());
        
        // Set HttpOnly cookie with JWT token (secure against XSS)
        String token = (String) authResult.get("token");
        Cookie cookie = new Cookie("brokr_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24 hours (matches JWT expiration)
        response.addCookie(cookie);
        
        // Remove token from response body for security
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletResponse response) {
        // Clear the cookie
        Cookie cookie = new Cookie("brokr_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Delete the cookie
        response.addCookie(cookie);
        
        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");
        return result;
    }

    @PostMapping("/register")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Map<String, Object> register(@RequestBody UserInput input, HttpServletResponse response) {
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
                .isActive(input.isActive()) // UserManagementService will handle default
                .build();

        Map<String, Object> authResult = authenticationService.register(user);
        
        // Set HttpOnly cookie with JWT token
        String token = (String) authResult.get("token");
        Cookie cookie = new Cookie("brokr_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24 hours
        response.addCookie(cookie);
        
        // Remove token from response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
    }
}