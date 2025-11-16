package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupCode {
    private String id;
    private String userId;
    private String codeHash; // BCrypt hash
    private boolean isUsed;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}

