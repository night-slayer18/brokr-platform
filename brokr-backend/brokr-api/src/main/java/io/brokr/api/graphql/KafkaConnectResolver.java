package io.brokr.api.graphql;

import io.brokr.api.input.KafkaConnectInput;
import io.brokr.api.service.KafkaConnectApiService;
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
    public KafkaConnect createKafkaConnect(@Argument KafkaConnectInput input) {
        return kafkaConnectApiService.createKafkaConnect(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnect updateKafkaConnect(@Argument String id, @Argument KafkaConnectInput input) {
        return kafkaConnectApiService.updateKafkaConnect(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public boolean deleteKafkaConnect(@Argument String id) {
        return kafkaConnectApiService.deleteKafkaConnect(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public boolean testKafkaConnectConnection(@Argument String id) {
        return kafkaConnectApiService.testKafkaConnectConnection(id);
    }
}