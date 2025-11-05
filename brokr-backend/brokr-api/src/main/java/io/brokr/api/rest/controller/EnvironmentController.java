package io.brokr.api.rest.controller;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.api.service.EnvironmentApiService;
import io.brokr.core.dto.EnvironmentDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.Environment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentApiService environmentApiService;

    @GetMapping("/organizations/{orgId}/environments")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#orgId)")
    public List<EnvironmentDto> getEnvironments(@PathVariable String orgId) {
        return environmentApiService.listEnvironments(orgId).stream()
                .map(EnvironmentDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public EnvironmentDto getEnvironment(@PathVariable String id) {
        return EnvironmentDto.fromDomain(environmentApiService.getEnvironmentById(id));
    }

    @PostMapping("/environments")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#input.organizationId)")
    public ResponseEntity<EnvironmentDto> createEnvironment(@RequestBody EnvironmentInput input) {
        Environment savedEnv = environmentApiService.createEnvironment(input);
        return new ResponseEntity<>(EnvironmentDto.fromDomain(savedEnv), HttpStatus.CREATED);
    }

    @PutMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public EnvironmentDto updateEnvironment(@PathVariable String id, @RequestBody EnvironmentInput input) {
        Environment updatedEnv = environmentApiService.updateEnvironment(id, input);
        return EnvironmentDto.fromDomain(updatedEnv);
    }

    @DeleteMapping("/environments/{id}")
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public ResponseEntity<Void> deleteEnvironment(@PathVariable String id) {
        if (!environmentApiService.deleteEnvironment(id)) {
            throw new ResourceNotFoundException("Environment not found with id: " + id);
        }
        return ResponseEntity.noContent().build();
    }
}