package io.brokr.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogStatistics {
    private long totalCount;
    private List<ActionTypeCount> byActionType;
    private List<ResourceTypeCount> byResourceType;
    private List<StatusCount> byStatus;
    private List<SeverityCount> bySeverity;
    private List<RecentActivity> recentActivity;
}
