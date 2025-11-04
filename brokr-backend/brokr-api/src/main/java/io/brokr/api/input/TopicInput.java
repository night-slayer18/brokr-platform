package io.brokr.api.input;


import lombok.Data;

import java.util.Map;

@Data
public class TopicInput {
    private String name;
    private int partitions;
    private int replicationFactor;
    private Map<String, String> configs;
}