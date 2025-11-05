package io.brokr.api.graphql;

import io.brokr.api.input.ResetOffsetInput;
import io.brokr.api.service.ConsumerGroupApiService;
import io.brokr.core.model.ConsumerGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ConsumerGroupResolver {

    private final ConsumerGroupApiService consumerGroupApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<ConsumerGroup> consumerGroups(@Argument String clusterId) {
        return consumerGroupApiService.listConsumerGroups(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public ConsumerGroup consumerGroup(@Argument String clusterId, @Argument String groupId) {
        return consumerGroupApiService.getConsumerGroup(clusterId, groupId);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public boolean resetConsumerGroupOffset(@Argument String clusterId, @Argument String groupId,
                                            @Argument String topic, @Argument int partition, @Argument long offset) {
        // Create the input DTO to pass to the service
        ResetOffsetInput input = new ResetOffsetInput();
        input.setTopic(topic);
        input.setPartition(partition);
        input.setOffset(offset);

        return consumerGroupApiService.resetConsumerGroupOffset(clusterId, groupId, input);
    }
}