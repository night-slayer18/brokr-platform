package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlQueryMetrics {
    private String id;
    private String queryHistoryId;
    private Double cpuUsagePercent;
    private Long memoryUsageMb;
    private Long rowsProcessedPerSecond;
    private Long bytesRead;
    private Long bytesWritten;
    private LocalDateTime timestamp;
}

