package io.brokr.api.graphql;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthenticationService authenticationService;

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
        
        if (response != null) {
            // Set HttpOnly cookie with JWT token (secure against XSS)
            String token = (String) authResult.get("token");
            Cookie cookie = new Cookie("brokr_token", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(isSecureRequest()); // Automatically set based on HTTPS
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 24 hours (matches JWT expiration)
            response.addCookie(cookie);
        }
        
        // Remove token from response body for security
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
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
                .isActive(input.isActive()) // UserManagementService will handle default
                .build();

        Map<String, Object> authResult = authenticationService.register(user);
        HttpServletResponse response = getResponse();
        
        if (response != null) {
            // Set HttpOnly cookie with JWT token
            String token = (String) authResult.get("token");
            Cookie cookie = new Cookie("brokr_token", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(isSecureRequest()); // Automatically set based on HTTPS
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 24 hours
            response.addCookie(cookie);
        }
        
        // Remove token from response body
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("user", authResult.get("user"));
        return responseBody;
    }
}