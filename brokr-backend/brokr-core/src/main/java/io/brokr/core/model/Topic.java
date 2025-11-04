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
public class Topic {
    private String name;
    private int partitions;
    private int replicationFactor;
    private List<PartitionInfo> partitionsInfo;
    private Map<String, String> configs;
    private boolean isInternal;
}