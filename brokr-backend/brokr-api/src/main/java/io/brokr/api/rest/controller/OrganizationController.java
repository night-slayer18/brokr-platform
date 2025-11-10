package io.brokr.api.rest.controller;

import io.brokr.api.input.OrganizationInput;
import io.brokr.api.service.OrganizationApiService;
import io.brokr.core.dto.OrganizationDto;
import io.brokr.core.model.Organization;
import io.brokr.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationApiService organizationApiService;
    private final AuthorizationService authorizationService;

    @GetMapping
    @PreAuthorize("@authorizationService.canManageOrganizations() or @authorizationService.getCurrentUser().role == T(io.brokr.core.model.Role).ADMIN")
    public List<OrganizationDto> getOrganizations() {
        // ADMIN can only see their own organization
        // SUPER_ADMIN can see all organizations
        var currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() == io.brokr.core.model.Role.ADMIN) {
            return List.of(organizationApiService.getOrganizationDtoById(currentUser.getOrganizationId()));
        }
        // This service method is optimized for the N+1 problem
        return organizationApiService.listOrganizationsWithEnvironments();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public OrganizationDto getOrganization(@PathVariable String id) {
        // This service method is optimized for the N+1 problem
        return organizationApiService.getOrganizationDtoById(id);
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public ResponseEntity<OrganizationDto> createOrganization(@RequestBody OrganizationInput input) {
        Organization savedOrg = organizationApiService.createOrganization(input);
        // Return DTO with empty environments list, which is correct for a new org
        return new ResponseEntity<>(OrganizationDto.fromDomain(savedOrg), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageOwnOrganization(#id)")
    public OrganizationDto updateOrganization(@PathVariable String id, @RequestBody OrganizationInput input) {
        // Service layer will validate ADMIN can only update their own organization
        // This service method is optimized for the N+1 problem
        return organizationApiService.updateAndGetOrganizationDto(id, input);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String id) {
        organizationApiService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}