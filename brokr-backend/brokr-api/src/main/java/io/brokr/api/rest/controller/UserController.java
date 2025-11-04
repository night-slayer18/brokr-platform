package io.brokr.api.rest.controller;

import io.brokr.api.input.UserInput;
import io.brokr.core.dto.UserDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.utils.PasswordValidator;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.fromDomain(authorizationService.getCurrentUser());
    }

    @GetMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserEntity::toDomain)
                .map(UserDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto getUserById(@PathVariable String id) {
        return userRepository.findById(id)
                .map(UserEntity::toDomain)
                .map(UserDto::fromDomain)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public ResponseEntity<UserDto> createUser(@RequestBody UserInput input) {
        if (userRepository.existsByUsername(input.getUsername())) {
            throw new ValidationException("Username already exists");
        }
        if (userRepository.existsByEmail(input.getEmail())) {
            throw new ValidationException("Email already exists");
        }

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

        User savedUser = userRepository.save(UserEntity.fromDomain(user)).toDomain();
        return new ResponseEntity<>(UserDto.fromDomain(savedUser), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto updateUser(@PathVariable String id, @RequestBody UserInput input) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        entity.setUsername(input.getUsername());
        entity.setEmail(input.getEmail());
        if (input.getPassword() != null && !input.getPassword().isEmpty()) {

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
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}