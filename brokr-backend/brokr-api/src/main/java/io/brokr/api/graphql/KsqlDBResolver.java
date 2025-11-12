package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.KsqlDBInput;
import io.brokr.api.service.KsqlDBApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.KsqlDB;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class KsqlDBResolver {

    private final KsqlDBApiService ksqlDBApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KsqlDB> ksqlDBs(@Argument String clusterId) {
        return ksqlDBApiService.listKsqlDBs(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#id)")
    public KsqlDB ksqlDB(@Argument String id) {
        return ksqlDBApiService.getKsqlDBById(id);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public String ksqlDBServerInfo(@Argument String ksqlDBId) {
        return ksqlDBApiService.getKsqlDBServerInfo(ksqlDBId);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.KSQLDB, resourceNameParam = "input.name", logResult = true)
    public KsqlDB createKsqlDB(@Argument KsqlDBInput input) {
        return ksqlDBApiService.createKsqlDB(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#id)")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.KSQLDB, resourceIdParam = "id", resourceNameParam = "input.name", logResult = true)
    public KsqlDB updateKsqlDB(@Argument String id, @Argument KsqlDBInput input) {
        return ksqlDBApiService.updateKsqlDB(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#id)")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.KSQLDB, resourceIdParam = "id")
    public boolean deleteKsqlDB(@Argument String id) {
        return ksqlDBApiService.deleteKsqlDB(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#id)")
    @AuditLoggable(action = AuditActionType.CONNECTION_TEST, resourceType = AuditResourceType.KSQLDB, resourceIdParam = "id", logResult = true)
    public boolean testKsqlDBConnection(@Argument String id) {
        return ksqlDBApiService.testKsqlDBConnection(id);
    }
}

