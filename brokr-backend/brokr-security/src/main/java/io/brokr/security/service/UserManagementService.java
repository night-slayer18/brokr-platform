package io.brokr.security.service;

import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.security.utils.PasswordValidator;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

        throw new RuntimeException("Access denied");
    }

    public Map<String, List<User>> getUsersForOrganizations(List<String> organizationIds) {
        return userRepository.findByOrganizationIdIn(organizationIds).stream()
                .map(UserEntity::toDomain)
                .collect(Collectors.groupingBy(User::getOrganizationId));
    }

    public User createUser(User user) {
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
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

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
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}