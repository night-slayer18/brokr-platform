package io.brokr.core.dto;

import io.brokr.core.model.KafkaStreamsApplication;
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
public class KafkaStreamsApplicationDto {
    private String id;
    private String name;
    private String applicationId;
    private String clusterId;
    private List<String> topics;
    private Map<String, Object> configuration;
    private boolean isActive;
    private String state;
    private List<ThreadMetadataDto> threads;

    public static KafkaStreamsApplicationDto fromDomain(KafkaStreamsApplication app) {
        return KafkaStreamsApplicationDto.builder()
                .id(app.getId())
                .name(app.getName())
                .applicationId(app.getApplicationId())
                .clusterId(app.getClusterId())
                .topics(app.getTopics())
                .configuration(app.getConfiguration())
                .isActive(app.isActive())
                .state(app.getState().name())
                .threads(app.getThreads().stream()
                        .map(ThreadMetadataDto::fromDomain)
                        .toList())
                .build();
    }
}