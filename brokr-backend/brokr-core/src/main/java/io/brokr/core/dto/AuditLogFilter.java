package io.brokr.core.dto;

import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.AuditSeverity;
import io.brokr.core.model.AuditStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogFilter {
    private String userId;
    private AuditActionType actionType;
    private AuditResourceType resourceType;
    private String resourceId;
    private String organizationId;
    private String clusterId;
    private AuditStatus status;
    private AuditSeverity severity;
    private Long startTime;
    private Long endTime;
    private String searchText;
}
