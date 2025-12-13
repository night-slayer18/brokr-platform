package io.brokr.core.dto;

import io.brokr.core.model.AuditResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTypeCount {
    private AuditResourceType resourceType;
    private long count;
}
