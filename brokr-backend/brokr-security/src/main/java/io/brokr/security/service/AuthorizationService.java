package io.brokr.security.service;

import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public boolean hasAccessToOrganization(String organizationId) {
        User user = getCurrentUser();

        // Super admins have access to all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Users can only access their own organization
        return user.getOrganizationId().equals(organizationId);
    }

    public boolean hasAccessToEnvironment(String environmentId) {
        User user = getCurrentUser();

        // Super admins have access to all environments
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Check if the environment is in the user's accessible environments
        return user.getAccessibleEnvironmentIds().contains(environmentId);
    }

    public boolean canManageTopics() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canManageUsers() {
        User user = getCurrentUser();
        return user.getRole() == Role.SERVER_ADMIN;
    }

    public boolean canManageOrganizations() {
        User user = getCurrentUser();
        return user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canAccessProdEnvironment() {
        User user = getCurrentUser();

        // Super admins can access prod
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Admins can access prod
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        // Viewers cannot access prod
        return false;
    }
}