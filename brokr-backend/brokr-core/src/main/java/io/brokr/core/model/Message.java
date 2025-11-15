package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private int partition;
    private long offset;
    private long timestamp;
    private String key;
    private String value;
    private Map<String, String> headers;  // Message headers for filtering
}
