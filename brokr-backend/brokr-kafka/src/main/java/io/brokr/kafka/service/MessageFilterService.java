package io.brokr.kafka.service;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.brokr.core.model.Message;
import io.brokr.core.model.MessageFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for evaluating message filters.
 * Optimized for performance with early termination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageFilterService {
    
    /**
     * Evaluates if a message matches the filter criteria.
     * Uses early termination for performance optimization.
     * 
     * @param message The message to evaluate
     * @param filter The filter criteria
     * @return true if message matches filter, false otherwise
     */
    public boolean matches(Message message, MessageFilter filter) {
        if (filter == null) {
            return true;  // No filter means all messages match
        }
        
        // If no filters are specified, all messages match
        if (filter.getKeyFilter() == null && 
            filter.getValueFilter() == null && 
            (filter.getHeaderFilters() == null || filter.getHeaderFilters().isEmpty()) &&
            filter.getTimestampRangeFilter() == null) {
            return true;
        }
        
        MessageFilter.FilterLogic logic = filter.getLogic() != null ? 
            filter.getLogic() : MessageFilter.FilterLogic.AND;
        
        if (logic == MessageFilter.FilterLogic.AND) {
            // All filters must match
            if (filter.getKeyFilter() != null && !matchesKeyFilter(message, filter.getKeyFilter())) {
                return false;
            }
            if (filter.getValueFilter() != null && !matchesValueFilter(message, filter.getValueFilter())) {
                return false;
            }
            if (filter.getHeaderFilters() != null && !filter.getHeaderFilters().isEmpty()) {
                if (!matchesHeaderFilters(message, filter.getHeaderFilters())) {
                    return false;
                }
            }
            if (filter.getTimestampRangeFilter() != null && !matchesTimestampRangeFilter(message, filter.getTimestampRangeFilter())) {
                return false;
            }
            return true;
        } else {
            // OR logic: At least one filter must match
            boolean matched = false;
            if (filter.getKeyFilter() != null && matchesKeyFilter(message, filter.getKeyFilter())) {
                matched = true;
            }
            if (filter.getValueFilter() != null && matchesValueFilter(message, filter.getValueFilter())) {
                matched = true;
            }
            if (filter.getHeaderFilters() != null && !filter.getHeaderFilters().isEmpty()) {
                if (matchesHeaderFilters(message, filter.getHeaderFilters())) {
                    matched = true;
                }
            }
            if (filter.getTimestampRangeFilter() != null && matchesTimestampRangeFilter(message, filter.getTimestampRangeFilter())) {
                matched = true;
            }
            return matched;
        }
    }
    
    /**
     * Evaluates key filter
     */
    private boolean matchesKeyFilter(Message message, MessageFilter.KeyFilter keyFilter) {
        if (message.getKey() == null) {
            return false;  // Message has no key, cannot match
        }
        
        String key = message.getKey();
        String filterValue = keyFilter.getValue();
        
        switch (keyFilter.getType()) {
            case EXACT:
                return key.equals(filterValue);
            case PREFIX:
                return key.startsWith(filterValue);
            case REGEX:
                try {
                    Pattern pattern = Pattern.compile(filterValue);
                    return pattern.matcher(key).matches();
                } catch (Exception e) {
                    log.warn("Invalid regex pattern for key filter: {}", filterValue, e);
                    return false;
                }
            case CONTAINS:
                return key.contains(filterValue);
            default:
                return false;
        }
    }
    
    /**
     * Evaluates value filter
     */
    private boolean matchesValueFilter(Message message, MessageFilter.ValueFilter valueFilter) {
        if (message.getValue() == null) {
            return false;  // Message has no value, cannot match
        }
        
        String value = message.getValue();
        String filterValue = valueFilter.getValue();
        
        switch (valueFilter.getType()) {
            case JSON_PATH:
                try {
                    // Parse JSON and evaluate JSONPath expression
                    Object result = JsonPath.read(value, filterValue);
                    return result != null;
                } catch (PathNotFoundException e) {
                    return false;  // Path not found means no match
                } catch (Exception e) {
                    log.warn("Invalid JSONPath expression or non-JSON value: {}", filterValue, e);
                    return false;
                }
            case REGEX:
                try {
                    Pattern pattern = Pattern.compile(filterValue);
                    return pattern.matcher(value).matches();
                } catch (Exception e) {
                    log.warn("Invalid regex pattern for value filter: {}", filterValue, e);
                    return false;
                }
            case CONTAINS:
                return value.contains(filterValue);
            case SIZE:
                long size = value.getBytes().length;
                if (valueFilter.getMinSize() != null && size < valueFilter.getMinSize()) {
                    return false;
                }
                if (valueFilter.getMaxSize() != null && size > valueFilter.getMaxSize()) {
                    return false;
                }
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Evaluates header filters (all must match for AND logic)
     */
    private boolean matchesHeaderFilters(Message message, List<MessageFilter.HeaderFilter> headerFilters) {
        if (message.getHeaders() == null || message.getHeaders().isEmpty()) {
            return false;  // Message has no headers, cannot match
        }
        
        Map<String, String> headers = message.getHeaders();
        
        for (MessageFilter.HeaderFilter headerFilter : headerFilters) {
            String headerKey = headerFilter.getHeaderKey();
            String headerValue = headerFilter.getHeaderValue();
            
            if (!headers.containsKey(headerKey)) {
                return false;  // Header key not found
            }
            
            if (headerValue != null) {
                String actualValue = headers.get(headerKey);
                if (headerFilter.isExactMatch()) {
                    if (!actualValue.equals(headerValue)) {
                        return false;
                    }
                } else {
                    if (!actualValue.contains(headerValue)) {
                        return false;
                    }
                }
            }
            // If headerValue is null, just check existence (already checked above)
        }
        
        return true;
    }
    
    /**
     * Evaluates timestamp range filter
     */
    private boolean matchesTimestampRangeFilter(Message message, MessageFilter.TimestampRangeFilter timestampRangeFilter) {
        LocalDateTime startTimestamp = timestampRangeFilter.getStartTimestamp();
        LocalDateTime endTimestamp = timestampRangeFilter.getEndTimestamp();
        
        // Convert message timestamp (milliseconds since epoch) to LocalDateTime
        LocalDateTime messageTimestamp = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(message.getTimestamp()),
            ZoneId.systemDefault()
        );
        
        if (startTimestamp != null && messageTimestamp.isBefore(startTimestamp)) {
            return false;
        }
        if (endTimestamp != null && messageTimestamp.isAfter(endTimestamp)) {
            return false;
        }
        
        return true;
    }
}

