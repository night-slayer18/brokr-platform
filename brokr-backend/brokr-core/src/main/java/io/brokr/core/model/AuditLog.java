package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Long id;
    private LocalDateTime timestamp;
    
    // User Information
    private String userId;
    private String userEmail;
    private String userRole;
    
    // Action Details
    private AuditActionType actionType;
    private AuditResourceType resourceType;
    private String resourceId;
    private String resourceName;
    
    // Context
    private String organizationId;
    private String environmentId;
    private String clusterId;
    
    // Request Details
    private String ipAddress;
    private String userAgent;
    private String requestId;
    
    // Change Details
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private List<String> changedFields;
    
    // Result
    private AuditStatus status;
    private String errorMessage;
    
    // Additional Metadata
    private Map<String, Object> metadata;
    private AuditSeverity severity;
    
    // Compliance
    private LocalDateTime retentionUntil;
    private boolean isSensitive;
}

