package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain model for API key rate limit configuration.
 * Defines rate limits for a specific API key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {
    private String id;
    private String apiKeyId;
    private String limitType; // 'per_second', 'per_minute', 'per_hour', 'per_day'
    private Integer limitValue; // Maximum number of requests
    private Integer windowSeconds; // Time window in seconds
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * Get the limit type enum.
     * @return The RateLimitType enum
     */
    public RateLimitType getLimitTypeEnum() {
        return RateLimitType.fromValue(limitType);
    }
    
    /**
     * Rate limit type enum.
     */
    public enum RateLimitType {
        PER_SECOND("per_second", 1),
        PER_MINUTE("per_minute", 60),
        PER_HOUR("per_hour", 3600),
        PER_DAY("per_day", 86400);
        
        private final String value;
        private final int windowSeconds;
        
        RateLimitType(String value, int windowSeconds) {
            this.value = value;
            this.windowSeconds = windowSeconds;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getWindowSeconds() {
            return windowSeconds;
        }
        
        public static RateLimitType fromValue(String value) {
            for (RateLimitType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }
}

