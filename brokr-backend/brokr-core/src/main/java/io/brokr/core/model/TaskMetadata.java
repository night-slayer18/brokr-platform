package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMetadata {
    private int taskId;
    private String taskIdString;
    private List<String> topicPartitions;
    private String taskState;
}