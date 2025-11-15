package io.brokr.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Transformation rules for message replay/reprocessing.
 * Optional - used when messages need to be transformed before reprocessing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTransformation {
    // Key transformation
    private KeyTransformation keyTransformation;
    
    // Value transformation
    private ValueTransformation valueTransformation;
    
    // Header transformations (add, modify, remove)
    private Map<String, String> headerAdditions;  // Headers to add/modify
    private List<String> headerRemovals;          // Headers to remove
    
    /**
     * Key transformation types
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyTransformation {
        private KeyTransformationType type;  // KEEP, REMOVE, MODIFY
        private String newValue;  // For MODIFY type
    }
    
    /**
     * Value transformation types
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueTransformation {
        private ValueTransformationType type;  // KEEP, MODIFY, FORMAT_CONVERSION
        private String newValue;  // For MODIFY type
        private String targetFormat;  // For FORMAT_CONVERSION (JSON, AVRO, etc.)
    }
    
    public enum KeyTransformationType {
        KEEP,    // Keep original key
        REMOVE,  // Remove key (set to null)
        MODIFY   // Modify key value
    }
    
    public enum ValueTransformationType {
        KEEP,              // Keep original value
        MODIFY,            // Modify value
        FORMAT_CONVERSION  // Convert format (e.g., JSON to Avro)
    }
}

