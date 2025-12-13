package io.brokr.core.dto;

import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivity {
    private long timestamp;
    private AuditActionType actionType;
    private AuditResourceType resourceType;
    private String resourceName;
    private String userEmail;
}
