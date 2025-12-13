package io.brokr.core.dto;

import io.brokr.core.model.AuditStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCount {
    private AuditStatus status;
    private long count;
}
