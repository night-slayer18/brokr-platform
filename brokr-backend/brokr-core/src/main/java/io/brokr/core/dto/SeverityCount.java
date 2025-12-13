package io.brokr.core.dto;

import io.brokr.core.model.AuditSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeverityCount {
    private AuditSeverity severity;
    private long count;
}
