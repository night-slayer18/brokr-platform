package io.brokr.storage.entity;

import io.brokr.core.model.BackupCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "backup_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupCodeEntity {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash; // BCrypt hash

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public BackupCode toDomain() {
        return BackupCode.builder()
                .id(id)
                .userId(userId)
                .codeHash(codeHash)
                .isUsed(isUsed)
                .usedAt(usedAt)
                .createdAt(createdAt)
                .build();
    }

    public static BackupCodeEntity fromDomain(BackupCode code) {
        return BackupCodeEntity.builder()
                .id(code.getId())
                .userId(code.getUserId())
                .codeHash(code.getCodeHash())
                .isUsed(code.isUsed())
                .usedAt(code.getUsedAt())
                .createdAt(code.getCreatedAt() != null ? code.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}

