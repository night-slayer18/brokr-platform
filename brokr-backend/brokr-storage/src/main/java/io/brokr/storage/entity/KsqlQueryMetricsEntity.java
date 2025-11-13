package io.brokr.storage.entity;

import io.brokr.core.model.KsqlQueryMetrics;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ksql_query_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlQueryMetricsEntity {
    @Id
    private String id;

    @Column(name = "query_history_id", nullable = false)
    private String queryHistoryId;

    @Column(name = "cpu_usage_percent", precision = 5, scale = 2)
    private java.math.BigDecimal cpuUsagePercent;

    @Column(name = "memory_usage_mb")
    private Long memoryUsageMb;

    @Column(name = "rows_processed_per_second")
    private Long rowsProcessedPerSecond;

    @Column(name = "bytes_read")
    private Long bytesRead;

    @Column(name = "bytes_written")
    private Long bytesWritten;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public KsqlQueryMetrics toDomain() {
        return KsqlQueryMetrics.builder()
                .id(id)
                .queryHistoryId(queryHistoryId)
                .cpuUsagePercent(cpuUsagePercent != null ? cpuUsagePercent.doubleValue() : null)
                .memoryUsageMb(memoryUsageMb)
                .rowsProcessedPerSecond(rowsProcessedPerSecond)
                .bytesRead(bytesRead)
                .bytesWritten(bytesWritten)
                .timestamp(timestamp)
                .build();
    }

    public static KsqlQueryMetricsEntity fromDomain(KsqlQueryMetrics metrics) {
        return KsqlQueryMetricsEntity.builder()
                .id(metrics.getId())
                .queryHistoryId(metrics.getQueryHistoryId())
                .cpuUsagePercent(metrics.getCpuUsagePercent() != null ? 
                    java.math.BigDecimal.valueOf(metrics.getCpuUsagePercent()) : null)
                .memoryUsageMb(metrics.getMemoryUsageMb())
                .rowsProcessedPerSecond(metrics.getRowsProcessedPerSecond())
                .bytesRead(metrics.getBytesRead())
                .bytesWritten(metrics.getBytesWritten())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}

