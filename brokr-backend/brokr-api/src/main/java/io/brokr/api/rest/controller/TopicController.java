package io.brokr.api.rest.controller;

import io.brokr.api.input.TopicInput;
import io.brokr.api.service.TopicApiService;
import io.brokr.core.dto.TopicDto;
import io.brokr.core.model.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicApiService topicApiService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public org.springframework.data.domain.Page<TopicDto> getTopics(
            @PathVariable String clusterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String search
    ) {
        return topicApiService.getTopicsPaginated(clusterId, page, size, search)
                .map(TopicDto::fromDomain);
    }

    @GetMapping("/{name}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public TopicDto getTopic(@PathVariable String clusterId, @PathVariable String name) {
        return TopicDto.fromDomain(topicApiService.getTopic(clusterId, name));
    }

    @PostMapping
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<TopicDto> createTopic(@PathVariable String clusterId, @RequestBody TopicInput input) {
        Topic newTopic = topicApiService.createTopic(clusterId, input);
        return new ResponseEntity<>(TopicDto.fromDomain(newTopic), HttpStatus.CREATED);
    }

    @PutMapping("/{name}/config")
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public TopicDto updateTopicConfig(@PathVariable String clusterId, @PathVariable String name, @RequestBody Map<String, String> configs) {
        Topic updatedTopic = topicApiService.updateTopicConfig(clusterId, name, configs);
        return TopicDto.fromDomain(updatedTopic);
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("@authorizationService.canManageTopics() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<Void> deleteTopic(@PathVariable String clusterId, @PathVariable String name) {
        topicApiService.deleteTopic(clusterId, name);
        return ResponseEntity.noContent().build();
    }
}