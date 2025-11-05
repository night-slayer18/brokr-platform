package io.brokr.api.graphql;

import io.brokr.api.input.LoginInput;
import io.brokr.api.input.UserInput;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthResolver {

    private final AuthenticationService authenticationService;

    @MutationMapping
    public Map<String, Object> login(@Argument LoginInput input) {
        return authenticationService.authenticate(input.getUsername(), input.getPassword());
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

        return authenticationService.register(user);
    }
}