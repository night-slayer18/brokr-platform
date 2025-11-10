package io.brokr.security.service;

import io.brokr.core.dto.UserDto;
import io.brokr.core.model.User;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserManagementService userManagementService; // Correct dependency

    public Map<String, Object> register(User user) {
        // Delegate user creation and validation to the centralized UserManagementService
        User savedUser = userManagementService.createUser(user);

        // After successful creation, generate a token
        String jwtToken = jwtService.generateToken(savedUser);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(savedUser));

        return response;
    }

    public Map<String, Object> authenticate(String email, String password) {
        // Note: The parameter is named 'email' but API field remains 'username' for backward compatibility
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        User user = userRepository.findByEmail(email)
                .map(UserEntity::toDomain)
                .orElseThrow();

        String jwtToken = jwtService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtToken);
        response.put("user", UserDto.fromDomain(user));

        return response;
    }
}