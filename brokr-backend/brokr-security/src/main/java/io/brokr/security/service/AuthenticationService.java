package io.brokr.security.service;

import io.brokr.api.exception.ValidationException;
import io.brokr.core.dto.UserDto;
import io.brokr.core.model.User;
import io.brokr.security.utils.PasswordValidator;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.brokr.storage.entity.UserEntity.fromDomain;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordValidator passwordValidator;

    public Map<String, Object> register(User request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        List<String> passwordErrors = passwordValidator.validatePassword(request.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new ValidationException("Invalid password: " + String.join(", ", passwordErrors));
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .organizationId(request.getOrganizationId())
                .accessibleEnvironmentIds(request.getAccessibleEnvironmentIds())
                .isActive(true)
                .build();

        userRepository.save(fromDomain(user));

        String jwtToken = jwtService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(user));

        return response;
    }

    public Map<String, Object> authenticate(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        User user = userRepository.findByUsername(username)
                .map(UserEntity::toDomain)
                .orElseThrow();

        String jwtToken = jwtService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(user));

        return response;
    }
}