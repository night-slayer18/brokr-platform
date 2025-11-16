package io.brokr.storage.entity;

import io.brokr.core.model.MfaDevice;
import io.brokr.core.model.MfaType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "mfa_devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaDeviceEntity {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private MfaType type;

    @Column(name = "name")
    private String name;

    @Column(name = "secret_key", nullable = false)
    private String secretKey; // Encrypted secret

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public MfaDevice toDomain() {
        return MfaDevice.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .name(name)
                .secretKey(secretKey)
                .isVerified(isVerified)
                .isActive(isActive)
                .lastUsedAt(lastUsedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    public static MfaDeviceEntity fromDomain(MfaDevice device) {
        return MfaDeviceEntity.builder()
                .id(device.getId())
                .userId(device.getUserId())
                .type(device.getType())
                .name(device.getName())
                .secretKey(device.getSecretKey())
                .isVerified(device.isVerified())
                .isActive(device.isActive())
                .lastUsedAt(device.getLastUsedAt())
                .createdAt(device.getCreatedAt() != null ? device.getCreatedAt() : LocalDateTime.now())
                .updatedAt(device.getUpdatedAt() != null ? device.getUpdatedAt() : LocalDateTime.now())
                .build();
    }
}

