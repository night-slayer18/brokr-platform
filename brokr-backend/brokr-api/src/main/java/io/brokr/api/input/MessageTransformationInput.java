package io.brokr.api.input;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Input for message transformation rules.
 */
@Data
public class MessageTransformationInput {
    private KeyTransformationInput keyTransformation;
    private ValueTransformationInput valueTransformation;
    private Map<String, String> headerAdditions;  // Headers to add/modify
    private List<String> headerRemovals;  // Headers to remove
    
    @Data
    public static class KeyTransformationInput {
        private KeyTransformationType type;  // KEEP, REMOVE, MODIFY
        private String newValue;  // For MODIFY type
    }
    
    @Data
    public static class ValueTransformationInput {
        private ValueTransformationType type;  // KEEP, MODIFY, FORMAT_CONVERSION
        private String newValue;  // For MODIFY type
        private String targetFormat;  // For FORMAT_CONVERSION (JSON, AVRO, etc.)
    }
    
    public enum KeyTransformationType {
        KEEP,
        REMOVE,
        MODIFY
    }
    
    public enum ValueTransformationType {
        KEEP,
        MODIFY,
        FORMAT_CONVERSION
    }
}

