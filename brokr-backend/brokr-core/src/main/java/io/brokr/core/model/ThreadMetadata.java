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
public class ThreadMetadata {
    private String threadName;
    private String threadState;
    private List<String> consumerClientId;
    private List<TaskMetadata> tasks;
}