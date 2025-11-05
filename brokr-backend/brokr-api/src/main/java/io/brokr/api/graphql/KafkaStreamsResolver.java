package io.brokr.api.graphql;

import io.brokr.api.input.KafkaStreamApplicationInput;
import io.brokr.api.service.KafkaStreamsApiService;
import io.brokr.core.model.KafkaStreamsApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class KafkaStreamsResolver {

    private final KafkaStreamsApiService kafkaStreamsApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaStreamsApplication> kafkaStreamsApplications(@Argument String clusterId) {
        return kafkaStreamsApiService.listKafkaStreamsApplications(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplication kafkaStreamsApplication(@Argument String id) {
        return kafkaStreamsApiService.getKafkaStreamsApplication(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public KafkaStreamsApplication createKafkaStreamsApplication(@Argument KafkaStreamApplicationInput input) {
        return kafkaStreamsApiService.createKafkaStreamsApplication(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplication updateKafkaStreamsApplication(@Argument String id, @Argument KafkaStreamApplicationInput input) {
        return kafkaStreamsApiService.updateKafkaStreamsApplication(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public boolean deleteKafkaStreamsApplication(@Argument String id) {
        return kafkaStreamsApiService.deleteKafkaStreamsApplication(id);
    }
}