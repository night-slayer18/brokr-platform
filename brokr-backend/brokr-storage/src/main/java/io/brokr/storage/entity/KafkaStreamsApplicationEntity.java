package io.brokr.storage.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokr.core.model.KafkaStreamsApplication;
import io.brokr.core.model.StreamsState;
import io.brokr.core.model.ThreadMetadata;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "kafka_streams_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaStreamsApplicationEntity {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    // FIX: Add logger
    private static final Logger log = LoggerFactory.getLogger(KafkaStreamsApplicationEntity.class);


    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] topics;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> configuration;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StreamsState state;

    @JdbcTypeCode(SqlTypes.JSON)
    private Object threads;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", updatable = false, insertable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", insertable = false, updatable = false)
    private KafkaClusterEntity cluster;


    public KafkaStreamsApplication toDomain() {
        List<ThreadMetadata> threadList = List.of();
        if (threads != null) {
            try {
                threadList = objectMapper.convertValue(threads, new TypeReference<>() {
                });
            } catch (Exception e) {
                log.error("Failed to deserialize threads JSON for entity {}: {}", id, e.getMessage());
            }
        }

        return KafkaStreamsApplication.builder()
                .id(id)
                .name(name)
                .applicationId(applicationId)
                .clusterId(clusterId)
                .topics(topics != null ? List.of(topics) : List.of())
                .configuration(configuration)
                .isActive(isActive)
                .state(state)
                .threads(threadList)
                .build();
    }

    public static KafkaStreamsApplicationEntity fromDomain(KafkaStreamsApplication app) {
        return KafkaStreamsApplicationEntity.builder()
                .id(app.getId())
                .name(app.getName())
                .applicationId(app.getApplicationId())
                .clusterId(app.getClusterId())
                .topics(app.getTopics() != null ? app.getTopics().toArray(new String[0]) : null)
                .configuration(app.getConfiguration())
                .isActive(app.isActive())
                .state(app.getState())
                .threads(app.getThreads())
                .build();
    }
}