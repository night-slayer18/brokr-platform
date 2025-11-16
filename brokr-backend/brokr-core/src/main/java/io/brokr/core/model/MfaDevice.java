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
public class MfaDevice {
    private String id;
    private String userId;
    private MfaType type;
    private String name;
    private String secretKey; // Encrypted secret
    private boolean isVerified;
    private boolean isActive;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

