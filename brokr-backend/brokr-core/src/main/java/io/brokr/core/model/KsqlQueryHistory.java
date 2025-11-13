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
public class KsqlQueryHistory {
    private String id;
    private String ksqlDbId;
    private String userId;
    private String queryText;
    private String queryType;
    private String status;
    private Long executionTimeMs;
    private Integer rowsReturned;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
}

