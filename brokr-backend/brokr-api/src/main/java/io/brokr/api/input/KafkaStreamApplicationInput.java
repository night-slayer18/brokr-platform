package io.brokr.api.input;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KafkaStreamApplicationInput {
    private String id;
    private String name;
    private String applicationId;
    private String clusterId;
    private List<String> topics;
    private Map<String, Object> configuration;
    private boolean isActive;
}
