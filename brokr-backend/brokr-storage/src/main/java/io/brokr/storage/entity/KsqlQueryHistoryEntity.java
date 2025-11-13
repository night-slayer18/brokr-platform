package io.brokr.storage.entity;

import io.brokr.core.model.KsqlQueryHistory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "ksql_query_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlQueryHistoryEntity {
    @Id
    private String id;

    @Column(name = "ksql_db_id", nullable = false)
    private String ksqlDbId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "query_type", nullable = false, length = 50)
    private String queryType;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Column(name = "rows_returned")
    private Integer rowsReturned;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    public KsqlQueryHistory toDomain() {
        return KsqlQueryHistory.builder()
                .id(id)
                .ksqlDbId(ksqlDbId)
                .userId(userId)
                .queryText(queryText)
                .queryType(queryType)
                .status(status)
                .executionTimeMs(executionTimeMs)
                .rowsReturned(rowsReturned)
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .properties(properties != null ? properties : new HashMap<>())
                .build();
    }

    public static KsqlQueryHistoryEntity fromDomain(KsqlQueryHistory history) {
        return KsqlQueryHistoryEntity.builder()
                .id(history.getId())
                .ksqlDbId(history.getKsqlDbId())
                .userId(history.getUserId())
                .queryText(history.getQueryText())
                .queryType(history.getQueryType())
                .status(history.getStatus())
                .executionTimeMs(history.getExecutionTimeMs())
                .rowsReturned(history.getRowsReturned())
                .errorMessage(history.getErrorMessage())
                .startedAt(history.getStartedAt())
                .completedAt(history.getCompletedAt())
                .properties(history.getProperties() != null ? history.getProperties() : new HashMap<>())
                .build();
    }
}

