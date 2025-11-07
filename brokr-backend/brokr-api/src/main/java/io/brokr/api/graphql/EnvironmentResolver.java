package io.brokr.api.graphql;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.api.service.EnvironmentApiService;
import io.brokr.api.service.OrganizationApiService;
import io.brokr.core.model.Environment;
import io.brokr.core.model.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import org.springframework.graphql.data.method.annotation.BatchMapping;

import org.springframework.graphql.data.method.annotation.BatchMapping;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class EnvironmentResolver {

    private final EnvironmentApiService environmentApiService;
    private final OrganizationApiService organizationApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#organizationId)")
    public List<Environment> environments(@Argument String organizationId) {
        return environmentApiService.listEnvironments(organizationId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public Environment environment(@Argument String id) {
        return environmentApiService.getEnvironmentById(id);
    }

    @BatchMapping(typeName = "Environment", field = "organization")
    public Map<Environment, Organization> getOrganization(List<Environment> environments) {
        List<String> organizationIds = environments.stream()
            .map(Environment::getOrganizationId)
            .distinct()
            .toList();

        Map<String, Organization> organizationsById = organizationApiService.getOrganizationsByIds(organizationIds);

        return environments.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                env -> organizationsById.get(env.getOrganizationId())
            ));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToOrganization(#input.organizationId)")
    public Environment createEnvironment(@Argument EnvironmentInput input) {
        return environmentApiService.createEnvironment(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public Environment updateEnvironment(@Argument String id, @Argument EnvironmentInput input) {
        return environmentApiService.updateEnvironment(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#id)")
    public boolean deleteEnvironment(@Argument String id) {
        return environmentApiService.deleteEnvironment(id);
    }
}