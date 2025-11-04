package io.brokr.api.graphql;

import io.brokr.api.input.UserInput;
import io.brokr.core.dto.UserDto;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.utils.PasswordValidator;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class UserResolver {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    @QueryMapping
    public UserDto me() {
        return UserDto.fromDomain(authorizationService.getCurrentUser());
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId) or @authorizationService.canManageUsers()")
    public List<UserDto> users(@Argument String organizationId) {
        if (organizationId != null) {
            return userRepository.findByOrganizationId(organizationId).stream()
                    .map(UserEntity::toDomain)
                    .map(UserDto::fromDomain)
                    .toList();
        }

        // Only super admins can list all users
        if (authorizationService.getCurrentUser().getRole() == io.brokr.core.model.Role.SUPER_ADMIN) {
            return userRepository.findAll().stream()
                    .map(UserEntity::toDomain)
                    .map(UserDto::fromDomain)
                    .toList();
        }

        throw new RuntimeException("Access denied");
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto user(@Argument String id) {
        return userRepository.findById(id)
                .map(UserEntity::toDomain)
                .map(UserDto::fromDomain)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public UserDto createUser(@Argument UserInput input) {
        if (userRepository.existsByUsername(input.getUsername())) {
            throw new ValidationException("Username already exists");
        }

        if (userRepository.existsByEmail(input.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        // FIX: Validate Password
        List<String> passwordErrors = passwordValidator.validatePassword(input.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new ValidationException("Invalid password: " + String.join(", ", passwordErrors));
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(input.getUsername())
                .email(input.getEmail())
                .password(passwordEncoder.encode(input.getPassword()))
                .firstName(input.getFirstName())
                .lastName(input.getLastName())
                .role(input.getRole())
                .organizationId(input.getOrganizationId())
                .accessibleEnvironmentIds(input.getAccessibleEnvironmentIds())
                .isActive(input.isActive())
                .build();

        User savedUser = userRepository.save(io.brokr.storage.entity.UserEntity.fromDomain(user))
                .toDomain();
        return UserDto.fromDomain(savedUser);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto updateUser(@Argument String id, @Argument UserInput input) {
        return userRepository.findById(id)
                .map(entity -> {
                    entity.setUsername(input.getUsername());
                    entity.setEmail(input.getEmail());
                    if (input.getPassword() != null && !input.getPassword().isEmpty()) {

                        // FIX: Validate new password
                        List<String> passwordErrors = passwordValidator.validatePassword(input.getPassword());
                        if (!passwordErrors.isEmpty()) {
                            throw new ValidationException("Invalid password: " + String.join(", ", passwordErrors));
                        }
                        entity.setPassword(passwordEncoder.encode(input.getPassword()));
                    }
                    entity.setFirstName(input.getFirstName());
                    entity.setLastName(input.getLastName());
                    entity.setRole(input.getRole());
                    entity.setOrganizationId(input.getOrganizationId());
                    entity.setAccessibleEnvironmentIds(input.getAccessibleEnvironmentIds());
                    entity.setActive(input.isActive());

                    User updatedUser = userRepository.save(entity).toDomain();
                    return UserDto.fromDomain(updatedUser);
                })
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public boolean deleteUser(@Argument String id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}