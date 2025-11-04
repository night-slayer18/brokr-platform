package io.brokr.core.dto;

import io.brokr.core.model.Topic;
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
public class TopicDto {
    private String name;
    private int partitions;
    private int replicationFactor;
    private List<PartitionInfoDto> partitionsInfo;
    private Map<String, String> configs;
    private boolean isInternal;

    public static TopicDto fromDomain(Topic topic) {
        return TopicDto.builder()
                .name(topic.getName())
                .partitions(topic.getPartitions())
                .replicationFactor(topic.getReplicationFactor())
                .isInternal(topic.isInternal())
                .configs(topic.getConfigs())
                .build();
    }
}