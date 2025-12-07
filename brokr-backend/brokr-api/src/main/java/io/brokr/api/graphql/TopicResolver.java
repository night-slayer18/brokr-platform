package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.MessageInput;
import io.brokr.api.input.TopicInput;
import io.brokr.api.service.TopicApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Message;
import io.brokr.core.model.Topic;
import io.brokr.kafka.service.KafkaConsumerService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class TopicResolver {

    private final TopicApiService topicApiService;
    private final KafkaClusterRepository clusterRepository;
    private final KafkaConsumerService kafkaConsumerService;

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadTopics()")
    public TopicPage topics(
            @Argument String clusterId,
            @Argument Integer page,
            @Argument Integer size,
            @Argument String search
    ) {
        Page<Topic> topicPage = topicApiService.getTopicsPaginated(
            clusterId, 
            page != null ? page : 0, 
            size != null ? size : 20, 
            search
        );
        
        return new TopicPage(
            topicPage.getContent(),
            topicPage.getTotalElements(),
            topicPage.getTotalPages(),
            topicPage.getNumber(),
            topicPage.getSize()
        );
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadTopics()")
    public Topic topic(@Argument String clusterId, @Argument String name) {
        return topicApiService.getTopic(clusterId, name);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadMessages()")
    public List<Message> messages(@Argument String clusterId, @Argument MessageInput input) {
        KafkaCluster cluster = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("Cluster not found with id: " + clusterId));

        return kafkaConsumerService.consumeMessages(
                cluster,
                input.getTopic(),
                input.getPartitions(),
                input.getOffset(),
                input.getLimit()
        );
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.TOPIC, resourceNameParam = "input.name", logResult = true)
    public Topic createTopic(@Argument String clusterId, @Argument TopicInput input) {
        return topicApiService.createTopic(clusterId, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.TOPIC, resourceIdParam = "name", resourceNameParam = "name", logResult = true)
    public Topic updateTopic(@Argument String clusterId, @Argument String name, @Argument Map<String, String> configs) {
        return topicApiService.updateTopicConfig(clusterId, name, configs);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.TOPIC, resourceIdParam = "name", resourceNameParam = "name")
    public boolean deleteTopic(@Argument String clusterId, @Argument String name) {
        return topicApiService.deleteTopic(clusterId, name);
    }

    public static class TopicPage {
        private final List<Topic> content;
        private final long totalElements;
        private final int totalPages;
        private final int currentPage;
        private final int pageSize;

        public TopicPage(List<Topic> content, long totalElements, int totalPages, int currentPage, int pageSize) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
        }

        public List<Topic> getContent() { return content; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
    }
}