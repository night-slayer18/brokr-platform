package io.brokr.core.dto;

import io.brokr.core.model.TopicPartition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicPartitionDto {
    private String topic;
    private int partition;

    public static TopicPartitionDto fromDomain(TopicPartition topicPartition) {
        return TopicPartitionDto.builder()
                .topic(topicPartition.getTopic())
                .partition(topicPartition.getPartition())
                .build();
    }
}