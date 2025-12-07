package io.brokr.api.rest.controller;

import io.brokr.api.input.ResetOffsetInput;
import io.brokr.api.service.ConsumerGroupApiService;
import io.brokr.core.dto.ConsumerGroupDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/brokr/clusters/{clusterId}/consumer-groups")
@RequiredArgsConstructor
public class ConsumerGroupController {

    private final ConsumerGroupApiService consumerGroupApiService;

    @GetMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadConsumerGroups()")
    public List<ConsumerGroupDto> getConsumerGroups(@PathVariable String clusterId) {
        return consumerGroupApiService.listConsumerGroups(clusterId).stream()
                .map(ConsumerGroupDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId) and @authorizationService.canReadConsumerGroups()")
    public ConsumerGroupDto getConsumerGroup(@PathVariable String clusterId, @PathVariable String groupId) {
        return ConsumerGroupDto.fromDomain(consumerGroupApiService.getConsumerGroup(clusterId, groupId));
    }

    @PostMapping("/{groupId}/reset-offset")
    @PreAuthorize("@authorizationService.canManageConsumerGroups() and @authorizationService.hasAccessToCluster(#clusterId)")
    public ResponseEntity<Boolean> resetOffset(
            @PathVariable String clusterId,
            @PathVariable String groupId,
            @RequestBody @Valid ResetOffsetInput input) { // Use the new DTO

        // All logic is now in the service
        boolean success = consumerGroupApiService.resetConsumerGroupOffset(clusterId, groupId, input);
        return ResponseEntity.ok(success);
    }
}