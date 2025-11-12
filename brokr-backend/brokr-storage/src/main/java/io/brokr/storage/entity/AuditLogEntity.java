package io.brokr.storage.entity;

import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditLog;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.AuditSeverity;
import io.brokr.core.model.AuditStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // User Information
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_role", length = 50)
    private String userRole;

    // Action Details
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private AuditResourceType resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "resource_name", length = 500)
    private String resourceName;

    // Context
    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "environment_id")
    private String environmentId;

    @Column(name = "cluster_id")
    private String clusterId;

    // Request Details
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    // Change Details
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> oldValues = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> newValues = new HashMap<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_fields")
    @Builder.Default
    private List<String> changedFields = new ArrayList<>();

    // Result
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Additional Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private AuditSeverity severity = AuditSeverity.INFO;

    // Compliance
    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    @Column(name = "is_sensitive")
    @Builder.Default
    private boolean isSensitive = false;

    public AuditLog toDomain() {
        return AuditLog.builder()
                .id(id)
                .timestamp(timestamp)
                .userId(userId)
                .userEmail(userEmail)
                .userRole(userRole)
                .actionType(actionType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .organizationId(organizationId)
                .environmentId(environmentId)
                .clusterId(clusterId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId)
                .oldValues(oldValues != null ? new HashMap<>(oldValues) : null)
                .newValues(newValues != null ? new HashMap<>(newValues) : null)
                .changedFields(changedFields != null ? new ArrayList<>(changedFields) : null)
                .status(status)
                .errorMessage(errorMessage)
                .metadata(metadata != null ? new HashMap<>(metadata) : null)
                .severity(severity)
                .retentionUntil(retentionUntil)
                .isSensitive(isSensitive)
                .build();
    }

    public static AuditLogEntity fromDomain(AuditLog auditLog) {
        return AuditLogEntity.builder()
                .id(auditLog.getId())
                .timestamp(auditLog.getTimestamp() != null ? auditLog.getTimestamp() : LocalDateTime.now())
                .userId(auditLog.getUserId())
                .userEmail(auditLog.getUserEmail())
                .userRole(auditLog.getUserRole())
                .actionType(auditLog.getActionType())
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .resourceName(auditLog.getResourceName())
                .organizationId(auditLog.getOrganizationId())
                .environmentId(auditLog.getEnvironmentId())
                .clusterId(auditLog.getClusterId())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .requestId(auditLog.getRequestId())
                .oldValues(auditLog.getOldValues() != null ? new HashMap<>(auditLog.getOldValues()) : new HashMap<>())
                .newValues(auditLog.getNewValues() != null ? new HashMap<>(auditLog.getNewValues()) : new HashMap<>())
                .changedFields(auditLog.getChangedFields() != null ? new ArrayList<>(auditLog.getChangedFields()) : new ArrayList<>())
                .status(auditLog.getStatus())
                .errorMessage(auditLog.getErrorMessage())
                .metadata(auditLog.getMetadata() != null ? new HashMap<>(auditLog.getMetadata()) : new HashMap<>())
                .severity(auditLog.getSeverity() != null ? auditLog.getSeverity() : AuditSeverity.INFO)
                .retentionUntil(auditLog.getRetentionUntil())
                .isSensitive(auditLog.isSensitive())
                .build();
    }
}

