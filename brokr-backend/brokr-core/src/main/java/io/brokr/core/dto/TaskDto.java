package io.brokr.core.dto;

import io.brokr.core.model.Task;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private int id;
    private String state;
    private String workerId;
    private String trace;

    public static TaskDto fromDomain(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .state(task.getState())
                .workerId(task.getWorkerId())
                .trace(task.getTrace())
                .build();
    }
}