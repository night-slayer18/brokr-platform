package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.OrganizationInput;
import io.brokr.api.service.ClusterApiService;
import io.brokr.api.service.EnvironmentApiService;
import io.brokr.api.service.OrganizationApiService;
import io.brokr.api.service.UserApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.Environment;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Organization;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class OrganizationResolver {

    private final OrganizationApiService organizationApiService;
    private final EnvironmentApiService environmentApiService;
    private final UserApiService userApiService;
    private final ClusterApiService clusterApiService;
    private final AuthorizationService authorizationService;

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageOrganizations() or @authorizationService.getCurrentUser().role == T(io.brokr.core.model.Role).ADMIN")
    public List<Organization> organizations() {
        User currentUser = authorizationService.getCurrentUser();
        
        // ADMIN can only see their own organization
        if (currentUser.getRole() == Role.ADMIN) {
            return List.of(organizationApiService.getOrganizationById(currentUser.getOrganizationId()));
        }
        
        // SUPER_ADMIN can see all organizations
        return organizationApiService.listOrganizations();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#id)")
    public Organization organization(@Argument String id) {
        return organizationApiService.getOrganizationById(id);
    }

    @BatchMapping(typeName = "Organization", field = "environments")
    public Map<Organization, List<Environment>> getEnvironments(List<Organization> organizations) {
        List<String> organizationIds = organizations.stream()
                .map(Organization::getId)
                .toList();

        Map<String, List<Environment>> environmentsByOrgId = environmentApiService.getEnvironmentsForOrganizations(organizationIds);

        return organizations.stream()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        org -> environmentsByOrgId.getOrDefault(org.getId(), List.of())
                ));
    }

    @BatchMapping(typeName = "Organization", field = "users")
    public Map<Organization, List<User>> getUsers(List<Organization> organizations) {
        List<String> organizationIds = organizations.stream()
                .map(Organization::getId)
                .toList();

        Map<String, List<User>> usersByOrgId = userApiService.getUsersForOrganizations(organizationIds);

        return organizations.stream()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        org -> usersByOrgId.getOrDefault(org.getId(), List.of())
                ));
    }

    @BatchMapping(typeName = "Organization", field = "clusters")
    public Map<Organization, List<KafkaCluster>> getClusters(List<Organization> organizations) {
        List<String> organizationIds = organizations.stream()
                .map(Organization::getId)
                .toList();

        Map<String, List<KafkaCluster>> clustersByOrgId = clusterApiService.getClustersForOrganizations(organizationIds);

        return organizations.stream()
                .collect(Collectors.toMap(
                        java.util.function.Function.identity(),
                        org -> clustersByOrgId.getOrDefault(org.getId(), List.of())
                ));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.ORGANIZATION, resourceNameParam = "input.name", logResult = true)
    public Organization createOrganization(@Argument OrganizationInput input) {
        return organizationApiService.createOrganization(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOwnOrganization(#id)")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.ORGANIZATION, resourceIdParam = "id", resourceNameParam = "input.name", logResult = true)
    public Organization updateOrganization(@Argument String id, @Argument OrganizationInput input) {
        // Service layer will validate ADMIN can only update their own organization
        return organizationApiService.updateOrganization(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageOrganizations()")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.ORGANIZATION, resourceIdParam = "id")
    public boolean deleteOrganization(@Argument String id) {
        return organizationApiService.deleteOrganization(id);
    }
}