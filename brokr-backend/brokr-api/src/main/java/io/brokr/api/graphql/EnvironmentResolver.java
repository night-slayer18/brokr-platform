package io.brokr.api.graphql;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.api.service.EnvironmentApiService;
import io.brokr.core.model.Environment;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class EnvironmentResolver {

    private final EnvironmentApiService environmentApiService;

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