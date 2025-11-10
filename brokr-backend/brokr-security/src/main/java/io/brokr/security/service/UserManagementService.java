package io.brokr.security.service;

import io.brokr.core.exception.AccessDeniedException;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.security.utils.PasswordValidator;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuthorizationService authorizationService;

    public User getUserById(String id) {
        return userRepository.findById(id)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public List<User> listUsers(String organizationId) {
        if (organizationId != null) {
            return userRepository.findByOrganizationId(organizationId).stream()
                    .map(UserEntity::toDomain)
                    .toList();
        }

        // Only super admins can list all users
        if (authorizationService.getCurrentUser().getRole() == Role.SUPER_ADMIN) {
            return userRepository.findAll().stream()
                    .map(UserEntity::toDomain)
                    .toList();
        }

        throw new AccessDeniedException("Access denied");
    }

    public Map<String, List<User>> getUsersForOrganizations(List<String> organizationIds) {
        return userRepository.findByOrganizationIdIn(organizationIds).stream()
                .map(UserEntity::toDomain)
                .collect(Collectors.groupingBy(User::getOrganizationId));
    }

    public User createUser(User user) {
        User currentUser = authorizationService.getCurrentUser();
        
        // ADMIN can only create users in their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            if (!currentUser.getOrganizationId().equals(user.getOrganizationId())) {
                throw new AccessDeniedException("ADMIN can only create users in their own organization");
            }
            
            // ADMIN cannot create SUPER_ADMIN or SERVER_ADMIN users
            if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.SERVER_ADMIN) {
                throw new AccessDeniedException("ADMIN cannot create users with SUPER_ADMIN or SERVER_ADMIN roles");
            }
        }
        
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ValidationException("Username already exists");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        List<String> passwordErrors = passwordValidator.validatePassword(user.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new ValidationException("Invalid password: " + String.join(", ", passwordErrors));
        }

        // Ensure ID is set and encode password
        user.setId(UUID.randomUUID().toString());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Ensure user is active by default on creation
        user.setActive(true);

        return userRepository.save(UserEntity.fromDomain(user)).toDomain();
    }

    public User updateUser(String id, User userUpdates) {
        User currentUser = authorizationService.getCurrentUser();
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        User existingUser = entity.toDomain();
        
        // ADMIN can only update users in their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            if (!currentUser.getOrganizationId().equals(existingUser.getOrganizationId())) {
                throw new AccessDeniedException("ADMIN can only update users in their own organization");
            }
            
            // ADMIN cannot change organizationId
            if (!userUpdates.getOrganizationId().equals(existingUser.getOrganizationId())) {
                throw new AccessDeniedException("ADMIN cannot change user's organization");
            }
            
            // ADMIN cannot assign SUPER_ADMIN or SERVER_ADMIN roles
            if (userUpdates.getRole() == Role.SUPER_ADMIN || userUpdates.getRole() == Role.SERVER_ADMIN) {
                throw new AccessDeniedException("ADMIN cannot assign SUPER_ADMIN or SERVER_ADMIN roles");
            }
            
            // ADMIN cannot change existing SUPER_ADMIN or SERVER_ADMIN users
            if (existingUser.getRole() == Role.SUPER_ADMIN || existingUser.getRole() == Role.SERVER_ADMIN) {
                throw new AccessDeniedException("ADMIN cannot modify SUPER_ADMIN or SERVER_ADMIN users");
            }
        }

        // Check for username/email conflicts if they are being changed
        if (!entity.getUsername().equals(userUpdates.getUsername()) && userRepository.existsByUsername(userUpdates.getUsername())) {
            throw new ValidationException("Username already exists");
        }
        if (!entity.getEmail().equals(userUpdates.getEmail()) && userRepository.existsByEmail(userUpdates.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        entity.setUsername(userUpdates.getUsername());
        entity.setEmail(userUpdates.getEmail());

        // Check if password is provided and needs updating
        if (userUpdates.getPassword() != null && !userUpdates.getPassword().isEmpty()) {
            List<String> passwordErrors = passwordValidator.validatePassword(userUpdates.getPassword());
            if (!passwordErrors.isEmpty()) {
                throw new ValidationException("Invalid password: " + String.join(", ", passwordErrors));
            }
            entity.setPassword(passwordEncoder.encode(userUpdates.getPassword()));
        }

        entity.setFirstName(userUpdates.getFirstName());
        entity.setLastName(userUpdates.getLastName());
        entity.setRole(userUpdates.getRole());
        entity.setOrganizationId(userUpdates.getOrganizationId());
        entity.setAccessibleEnvironmentIds(userUpdates.getAccessibleEnvironmentIds());
        entity.setActive(userUpdates.isActive());

        return userRepository.save(entity).toDomain();
    }

    public boolean deleteUser(String id) {
        User currentUser = authorizationService.getCurrentUser();
        
        if (!userRepository.existsById(id)) {
            return false;
        }
        
        User userToDelete = getUserById(id);
        
        // ADMIN can only delete users in their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            if (!currentUser.getOrganizationId().equals(userToDelete.getOrganizationId())) {
                throw new AccessDeniedException("ADMIN can only delete users in their own organization");
            }
            
            // ADMIN cannot delete SUPER_ADMIN or SERVER_ADMIN users
            if (userToDelete.getRole() == Role.SUPER_ADMIN || userToDelete.getRole() == Role.SERVER_ADMIN) {
                throw new AccessDeniedException("ADMIN cannot delete SUPER_ADMIN or SERVER_ADMIN users");
            }
            
            // ADMIN cannot delete themselves
            if (currentUser.getId().equals(id)) {
                throw new AccessDeniedException("ADMIN cannot delete themselves");
            }
        }
        
        userRepository.deleteById(id);
        return true;
    }
}