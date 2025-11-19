package io.brokr.api.service;

import io.brokr.api.input.UserInput;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiService {

    private final UserManagementService userManagementService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        return authorizationService.getCurrentUser();
    }

    @Transactional(readOnly = true)
    public Page<User> listUsers(String organizationId, int page, int size) {
        return userManagementService.listUsers(organizationId, page, size);
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
        User savedUser = userManagementService.createUser(userModel);
        
        // Log audit event
        try {
            auditService.logCreate(AuditResourceType.USER, savedUser.getId(), savedUser.getEmail(), savedUser);
        } catch (Exception e) {
            log.warn("Failed to log audit event for user creation: {}", e.getMessage());
        }
        
        return savedUser;
    }

    @Transactional
    public User updateUser(String id, UserInput input) {
        User oldUser = userManagementService.getUserById(id);
        User userModel = convertInputToModel(input);
        User updatedUser = userManagementService.updateUser(id, userModel);
        
        // Log audit event
        try {
            auditService.logUpdate(AuditResourceType.USER, updatedUser.getId(), updatedUser.getEmail(), oldUser, updatedUser);
        } catch (Exception e) {
            log.warn("Failed to log audit event for user update: {}", e.getMessage());
        }
        
        return updatedUser;
    }

    @Transactional
    public boolean deleteUser(String id) {
        User user = userManagementService.getUserById(id);
        boolean result = userManagementService.deleteUser(id);
        
        // Log audit event
        try {
            auditService.logDelete(AuditResourceType.USER, user.getId(), user.getEmail(), user);
        } catch (Exception e) {
            log.warn("Failed to log audit event for user deletion: {}", e.getMessage());
        }
        
        return result;
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