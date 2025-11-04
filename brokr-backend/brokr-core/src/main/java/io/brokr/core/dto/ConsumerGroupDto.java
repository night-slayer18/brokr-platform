package io.brokr.core.dto;

import io.brokr.core.model.ConsumerGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerGroupDto {
    private String groupId;
    private String state;
    private List<MemberInfoDto> members;
    private Map<String, Long> topicOffsets;
    private String coordinator;

    public static ConsumerGroupDto fromDomain(ConsumerGroup consumerGroup) {
        return ConsumerGroupDto.builder()
                .groupId(consumerGroup.getGroupId())
                .state(consumerGroup.getState())
                .members(consumerGroup.getMembers().stream()
                        .map(MemberInfoDto::fromDomain)
                        .toList())
                .topicOffsets(consumerGroup.getTopicOffsets())
                .coordinator(consumerGroup.getCoordinator())
                .build();
    }
}