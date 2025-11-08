package io.brokr.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Message {
    private int partition;
    private long offset;
    private long timestamp;
    private String key;
    private String value;
}
