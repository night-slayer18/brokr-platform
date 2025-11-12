package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.SchemaRegistryInput;
import io.brokr.api.service.SchemaRegistryApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.SchemaRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class SchemaRegistryResolver {

    private final SchemaRegistryApiService schemaRegistryApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<SchemaRegistry> schemaRegistries(@Argument String clusterId) {
        return schemaRegistryApiService.listSchemaRegistries(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    public SchemaRegistry schemaRegistry(@Argument String id) {
        return schemaRegistryApiService.getSchemaRegistryById(id);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#schemaRegistryId)")
    public List<String> schemaRegistrySubjects(@Argument String schemaRegistryId) {
        return schemaRegistryApiService.getSchemaRegistrySubjects(schemaRegistryId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#schemaRegistryId)")
    public String schemaRegistryLatestSchema(@Argument String schemaRegistryId, @Argument String subject) {
        return schemaRegistryApiService.getSchemaRegistryLatestSchema(schemaRegistryId, subject);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#schemaRegistryId)")
    public List<Integer> schemaRegistrySchemaVersions(@Argument String schemaRegistryId, @Argument String subject) {
        return schemaRegistryApiService.getSchemaRegistrySchemaVersions(schemaRegistryId, subject);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.SCHEMA_REGISTRY, resourceNameParam = "input.name", logResult = true)
    public SchemaRegistry createSchemaRegistry(@Argument SchemaRegistryInput input) {
        return schemaRegistryApiService.createSchemaRegistry(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.SCHEMA_REGISTRY, resourceIdParam = "id", resourceNameParam = "input.name", logResult = true)
    public SchemaRegistry updateSchemaRegistry(@Argument String id, @Argument SchemaRegistryInput input) {
        return schemaRegistryApiService.updateSchemaRegistry(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.SCHEMA_REGISTRY, resourceIdParam = "id")
    public boolean deleteSchemaRegistry(@Argument String id) {
        return schemaRegistryApiService.deleteSchemaRegistry(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToSchemaRegistry(#id)")
    @AuditLoggable(action = AuditActionType.CONNECTION_TEST, resourceType = AuditResourceType.SCHEMA_REGISTRY, resourceIdParam = "id", logResult = true)
    public boolean testSchemaRegistryConnection(@Argument String id) {
        return schemaRegistryApiService.testSchemaRegistryConnection(id);
    }
}