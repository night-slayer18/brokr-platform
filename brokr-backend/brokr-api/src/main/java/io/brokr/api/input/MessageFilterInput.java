package io.brokr.api.input;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Input for message filter criteria.
 */
@Data
public class MessageFilterInput {
    private KeyFilterInput keyFilter;
    private ValueFilterInput valueFilter;
    private List<HeaderFilterInput> headerFilters;
    private TimestampRangeFilterInput timestampRangeFilter;
    private FilterLogic logic;  // AND or OR (default: AND)
    
    @Data
    public static class KeyFilterInput {
        private KeyFilterType type;  // EXACT, PREFIX, REGEX, CONTAINS
        private String value;
    }
    
    @Data
    public static class ValueFilterInput {
        private ValueFilterType type;  // JSON_PATH, REGEX, CONTAINS, SIZE
        private String value;
        private Long minSize;  // For SIZE filter
        private Long maxSize;  // For SIZE filter
    }
    
    @Data
    public static class HeaderFilterInput {
        private String headerKey;
        private String headerValue;  // NULL for existence check only
        private Boolean exactMatch;  // true for exact match, false for contains
    }
    
    @Data
    public static class TimestampRangeFilterInput {
        private LocalDateTime startTimestamp;
        private LocalDateTime endTimestamp;
    }
    
    public enum FilterLogic {
        AND,
        OR
    }
    
    public enum KeyFilterType {
        EXACT,
        PREFIX,
        REGEX,
        CONTAINS
    }
    
    public enum ValueFilterType {
        JSON_PATH,
        REGEX,
        CONTAINS,
        SIZE
    }
}

