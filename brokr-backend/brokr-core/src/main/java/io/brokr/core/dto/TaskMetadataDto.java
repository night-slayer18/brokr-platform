package io.brokr.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMetadataDto {
    private int taskId;
    private String taskIdString;
    private List<String> topicPartitions;
    private String taskState;

    public static TaskMetadataDto fromDomain(io.brokr.core.model.TaskMetadata taskMetadata) {
        return TaskMetadataDto.builder()
                .taskId(taskMetadata.getTaskId())
                .taskIdString(taskMetadata.getTaskIdString())
                .topicPartitions(taskMetadata.getTopicPartitions())
                .taskState(taskMetadata.getTaskState())
                .build();
    }
}