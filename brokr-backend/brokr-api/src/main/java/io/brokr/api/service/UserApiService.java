package io.brokr.api.service;

import io.brokr.api.input.UserInput;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserApiService {

    private final UserManagementService userManagementService;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        return authorizationService.getCurrentUser();
    }

    @Transactional(readOnly = true)
    public List<User> listUsers(String organizationId) {
        return userManagementService.listUsers(organizationId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<User>> getUsersForOrganizations(List<String> organizationIds) {
        return userManagementService.getUsersForOrganizations(organizationIds);
    }

    @Transactional(readOnly = true)
    public User getUserById(String id) {
        return userManagementService.getUserById(id);
    }

    @Transactional
    public User createUser(UserInput input) {
        User userModel = convertInputToModel(input);
        return userManagementService.createUser(userModel);
    }

    @Transactional
    public User updateUser(String id, UserInput input) {
        User userModel = convertInputToModel(input);
        return userManagementService.updateUser(id, userModel);
    }

    @Transactional
    public boolean deleteUser(String id) {
        return userManagementService.deleteUser(id);
    }

    // Helper to convert DTO to Model, keeping the API layer responsible for the DTO
    private User convertInputToModel(UserInput input) {
        // Use Boolean wrapper and default to true if null (for backward compatibility)
        boolean isActive = input.getIsActive() != null ? input.getIsActive() : true;
        return User.builder()
                .username(input.getUsername())
                .email(input.getEmail())
                .password(input.getPassword()) // Pass un-encoded password
                .firstName(input.getFirstName())
                .lastName(input.getLastName())
                .role(input.getRole())
                .organizationId(input.getOrganizationId())
                .accessibleEnvironmentIds(input.getAccessibleEnvironmentIds())
                .isActive(isActive)
                .build();
    }
}