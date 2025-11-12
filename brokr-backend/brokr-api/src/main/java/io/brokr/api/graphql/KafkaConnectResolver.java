package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.KafkaConnectInput;
import io.brokr.api.service.KafkaConnectApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.KafkaConnect;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class KafkaConnectResolver {

    private final KafkaConnectApiService kafkaConnectApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaConnect> kafkaConnects(@Argument String clusterId) {
        return kafkaConnectApiService.listKafkaConnects(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnect kafkaConnect(@Argument String id) {
        return kafkaConnectApiService.getKafkaConnectById(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.KAFKA_CONNECT, resourceNameParam = "input.name", logResult = true)
    public KafkaConnect createKafkaConnect(@Argument KafkaConnectInput input) {
        return kafkaConnectApiService.createKafkaConnect(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.KAFKA_CONNECT, resourceIdParam = "id", resourceNameParam = "input.name", logResult = true)
    public KafkaConnect updateKafkaConnect(@Argument String id, @Argument KafkaConnectInput input) {
        return kafkaConnectApiService.updateKafkaConnect(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.KAFKA_CONNECT, resourceIdParam = "id")
    public boolean deleteKafkaConnect(@Argument String id) {
        return kafkaConnectApiService.deleteKafkaConnect(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    @AuditLoggable(action = AuditActionType.CONNECTION_TEST, resourceType = AuditResourceType.KAFKA_CONNECT, resourceIdParam = "id", logResult = true)
    public boolean testKafkaConnectConnection(@Argument String id) {
        return kafkaConnectApiService.testKafkaConnectConnection(id);
    }
}