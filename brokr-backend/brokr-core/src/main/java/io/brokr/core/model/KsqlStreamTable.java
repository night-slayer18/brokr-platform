package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlStreamTable {
    private String id;
    private String ksqlDbId;
    private String name;
    private String type; // 'STREAM' or 'TABLE'
    private String topicName;
    private String keyFormat;
    private String valueFormat;
    private String schema; // JSON schema
    private String queryText; // Original CREATE statement
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
}

