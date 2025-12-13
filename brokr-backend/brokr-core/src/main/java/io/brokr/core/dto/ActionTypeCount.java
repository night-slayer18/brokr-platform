package io.brokr.core.dto;

import io.brokr.core.model.AuditActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionTypeCount {
    private AuditActionType actionType;
    private long count;
}
