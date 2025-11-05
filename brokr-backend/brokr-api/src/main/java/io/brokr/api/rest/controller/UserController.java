package io.brokr.api.rest.controller;

import io.brokr.api.input.UserInput;
import io.brokr.api.service.UserApiService;
import io.brokr.core.dto.UserDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApiService userApiService;

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.fromDomain(userApiService.getCurrentUser());
    }

    @GetMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public List<UserDto> getAllUsers() {
        return userApiService.listUsers(null).stream() // Pass null to get all users (if authorized)
                .map(UserDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto getUserById(@PathVariable String id) {
        return UserDto.fromDomain(userApiService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public ResponseEntity<UserDto> createUser(@RequestBody UserInput input) {
        User savedUser = userApiService.createUser(input);
        return new ResponseEntity<>(UserDto.fromDomain(savedUser), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers() or @authorizationService.getCurrentUser().id == #id")
    public UserDto updateUser(@PathVariable String id, @RequestBody UserInput input) {
        User updatedUser = userApiService.updateUser(id, input);
        return UserDto.fromDomain(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userApiService.deleteUser(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        return ResponseEntity.noContent().build();
    }
}