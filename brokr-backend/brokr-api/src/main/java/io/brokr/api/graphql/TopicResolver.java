package io.brokr.api.graphql;

import io.brokr.api.input.TopicInput;
import io.brokr.api.service.TopicApiService;
import io.brokr.core.model.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class TopicResolver {

    private final TopicApiService topicApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<Topic> topics(@Argument String clusterId) {
        return topicApiService.listTopics(clusterId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public Topic topic(@Argument String clusterId, @Argument String name) {
        return topicApiService.getTopic(clusterId, name);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public Topic createTopic(@Argument String clusterId, @Argument TopicInput input) {
        return topicApiService.createTopic(clusterId, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public Topic updateTopic(@Argument String clusterId, @Argument String name, @Argument Map<String, String> configs) {
        return topicApiService.updateTopicConfig(clusterId, name, configs);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public boolean deleteTopic(@Argument String clusterId, @Argument String name) {
        return topicApiService.deleteTopic(clusterId, name);
    }
}