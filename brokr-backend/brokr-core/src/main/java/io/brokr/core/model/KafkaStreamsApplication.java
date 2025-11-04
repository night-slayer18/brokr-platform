package io.brokr.core.model;

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
public class KafkaStreamsApplication {
    private String id;
    private String name;
    private String clusterId;
    private String applicationId;
    private List<String> topics;
    private Map<String, Object> configuration;
    private boolean isActive;
    private StreamsState state;
    private List<ThreadMetadata> threads;
}