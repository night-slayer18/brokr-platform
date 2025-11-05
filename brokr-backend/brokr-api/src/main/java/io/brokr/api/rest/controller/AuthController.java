package io.brokr.api.rest.controller;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginInput input) {
        return authenticationService.authenticate(input.getUsername(), input.getPassword());
    }

    @PostMapping("/register")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Map<String, Object> register(@RequestBody UserInput input) {
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

        return authenticationService.register(user);
    }
}