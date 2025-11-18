package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filter criteria for message replay/reprocessing.
 * Supports filtering by key, value, headers, and timestamp range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageFilter {
    // Key filters
    private KeyFilter keyFilter;
    
    // Value filters
    private ValueFilter valueFilter;
    
    // Header filters
    private List<HeaderFilter> headerFilters;
    
    // Timestamp range filter
    private TimestampRangeFilter timestampRangeFilter;
    
    // Filter combination logic (AND or OR)
    private FilterLogic logic;  // Default: AND
    
    /**
     * Key filter types
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyFilter {
        private KeyFilterType type;  // EXACT, PREFIX, REGEX, CONTAINS
        private String value;
    }
    
    /**
     * Value filter types
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueFilter {
        private ValueFilterType type;  // JSON_PATH, REGEX, CONTAINS, SIZE
        private String value;
        private Long minSize;  // For SIZE filter
        private Long maxSize;  // For SIZE filter
    }
    
    /**
     * Header filter
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderFilter {
        private String headerKey;
        private String headerValue;  // NULL for existence check only
        private boolean exactMatch;  // true for exact match, false for contains
    }
    
    /**
     * Timestamp range filter
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimestampRangeFilter {
        private LocalDateTime startTimestamp;
        private LocalDateTime endTimestamp;
    }
    
    /**
     * Filter combination logic
     */
    public enum FilterLogic {
        AND,  // All filters must match
        OR    // Any filter must match
    }
    
    /**
     * Key filter types
     */
    public enum KeyFilterType {
        EXACT,    // Exact match
        PREFIX,   // Starts with
        REGEX,    // Regular expression match
        CONTAINS  // Contains substring
    }
    
    /**
     * Value filter types
     */
    public enum ValueFilterType {
        JSON_PATH,  // JSON path expression (e.g., "$.status == 'error'")
        REGEX,      // Regular expression match
        CONTAINS,   // Contains substring
        SIZE        // Value size (min/max bytes)
    }
}

