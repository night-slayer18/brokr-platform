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
public class ThreadMetadataDto {
    private String threadName;
    private String threadState;
    private List<String> consumerClientId;
    private List<TaskMetadataDto> tasks;

    public static ThreadMetadataDto fromDomain(io.brokr.core.model.ThreadMetadata threadMetadata) {
        return ThreadMetadataDto.builder()
                .threadName(threadMetadata.getThreadName())
                .threadState(threadMetadata.getThreadState())
                .consumerClientId(threadMetadata.getConsumerClientId())
                .tasks(threadMetadata.getTasks().stream()
                        .map(TaskMetadataDto::fromDomain)
                        .toList())
                .build();
    }
}