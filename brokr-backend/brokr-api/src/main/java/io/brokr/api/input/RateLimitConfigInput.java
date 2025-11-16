package io.brokr.api.input;

import lombok.Data;

/**
 * Input for configuring rate limits for an API key.
 */
@Data
public class RateLimitConfigInput {
    private String limitType; // 'per_second', 'per_minute', 'per_hour', 'per_day'
    private Integer limitValue;
    private Integer windowSeconds;
}

