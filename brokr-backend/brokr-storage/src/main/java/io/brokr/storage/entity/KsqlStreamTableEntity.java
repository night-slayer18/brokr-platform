package io.brokr.storage.entity;

import io.brokr.core.model.KsqlStreamTable;
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
@Table(name = "ksql_streams_tables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlStreamTableEntity {
    @Id
    private String id;

    @Column(name = "ksql_db_id", nullable = false)
    private String ksqlDbId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String type; // 'STREAM' or 'TABLE'

    @Column(name = "topic_name", length = 255)
    private String topicName;

    @Column(name = "key_format", length = 50)
    private String keyFormat;

    @Column(name = "value_format", length = 50)
    private String valueFormat;

    @Column(columnDefinition = "TEXT")
    private String schema; // JSON schema

    @Column(name = "query_text", columnDefinition = "TEXT")
    private String queryText; // Original CREATE statement

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public KsqlStreamTable toDomain() {
        return KsqlStreamTable.builder()
                .id(id)
                .ksqlDbId(ksqlDbId)
                .name(name)
                .type(type)
                .topicName(topicName)
                .keyFormat(keyFormat)
                .valueFormat(valueFormat)
                .schema(schema)
                .queryText(queryText)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .properties(properties != null ? properties : new HashMap<>())
                .build();
    }

    public static KsqlStreamTableEntity fromDomain(KsqlStreamTable streamTable) {
        return KsqlStreamTableEntity.builder()
                .id(streamTable.getId())
                .ksqlDbId(streamTable.getKsqlDbId())
                .name(streamTable.getName())
                .type(streamTable.getType())
                .topicName(streamTable.getTopicName())
                .keyFormat(streamTable.getKeyFormat())
                .valueFormat(streamTable.getValueFormat())
                .schema(streamTable.getSchema())
                .queryText(streamTable.getQueryText())
                .createdAt(streamTable.getCreatedAt())
                .updatedAt(streamTable.getUpdatedAt())
                .properties(streamTable.getProperties() != null ? streamTable.getProperties() : new HashMap<>())
                .build();
    }
}

