package io.brokr.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokr.core.model.*;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.AuditLogEntity;
import io.brokr.storage.repository.AuditLogRepository;
import io.brokr.api.util.RequestContextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;
    private final RequestContextExtractor requestContextExtractor;
    private final ObjectMapper objectMapper;

    private static final List<String> SENSITIVE_FIELDS = Arrays.asList(
            "password", "saslPassword", "sslTruststorePassword",
            "sslKeystorePassword", "sslKeyPassword", "token", "secret",
            "key", "fullKey", "apiKey", // API key fields - should never be logged
            "properties" // properties can contain sensitive configs
    );
    
    // Fields to exclude from audit logs (internal/transient data)
    private static final List<String> EXCLUDED_FIELDS = Arrays.asList(
            "brokers", "topics", "consumerGroups", "schemaRegistries", 
            "kafkaConnects", "kafkaStreamsApplications", "ksqlDBs",
            "partitionsInfo", "cluster", "environment", "organization",
            "lastConnectionCheck", "lastConnectionError", "isReachable",
            "fullKey" // CRITICAL: Never log full API keys in audit logs
    );

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditLog auditLog) {
        try {
            // Set timestamp if not set
            if (auditLog.getTimestamp() == null) {
                auditLog.setTimestamp(LocalDateTime.now());
            }

            // Enrich with request context if not already set
            if (auditLog.getIpAddress() == null) {
                auditLog.setIpAddress(requestContextExtractor.getIpAddress());
            }
            if (auditLog.getUserAgent() == null) {
                auditLog.setUserAgent(requestContextExtractor.getUserAgent());
            }
            if (auditLog.getRequestId() == null) {
                auditLog.setRequestId(requestContextExtractor.getRequestId());
            }

            // Enrich with user context if not already set
            try {
                User currentUser = authorizationService.getCurrentUser();
                if (auditLog.getUserId() == null) {
                    auditLog.setUserId(currentUser.getId());
                }
                if (auditLog.getUserEmail() == null) {
                    auditLog.setUserEmail(currentUser.getEmail());
                }
                if (auditLog.getUserRole() == null && currentUser.getRole() != null) {
                    auditLog.setUserRole(currentUser.getRole().name());
                }
            } catch (Exception e) {
                // User context not available (e.g., unauthenticated request)
            }

            // Mask sensitive data
            maskSensitiveData(auditLog);

            // Determine severity if not set
            if (auditLog.getSeverity() == null) {
                auditLog.setSeverity(determineSeverity(auditLog));
            }

            // Set default status if not set
            if (auditLog.getStatus() == null) {
                auditLog.setStatus(AuditStatus.SUCCESS);
            }

            // Save to database
            AuditLogEntity entity = AuditLogEntity.fromDomain(auditLog);
            auditLogRepository.saveAndFlush(entity);

        } catch (Exception e) {
            // Never fail the main operation due to audit logging failure
            log.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }

    public void logCreate(AuditResourceType resourceType, String resourceId, String resourceName, Object newValue) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.CREATE, resourceType, resourceId, resourceName);
        auditLog.setNewValues(extractRelevantFields(newValue));
        logAction(auditLog);
    }

    public void logUpdate(AuditResourceType resourceType, String resourceId, String resourceName, Object oldValue, Object newValue) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.UPDATE, resourceType, resourceId, resourceName);
        auditLog.setOldValues(extractRelevantFields(oldValue));
        auditLog.setNewValues(extractRelevantFields(newValue));
        auditLog.setChangedFields(identifyChangedFields(oldValue, newValue));
        logAction(auditLog);
    }

    public void logDelete(AuditResourceType resourceType, String resourceId, String resourceName, Object oldValue) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.DELETE, resourceType, resourceId, resourceName);
        // For DELETE, only store minimal info (name, id) to avoid storing sensitive data
        Map<String, Object> minimalInfo = new HashMap<>();
        if (oldValue != null) {
            Map<String, Object> fullMap = convertToMap(oldValue);
            minimalInfo.put("id", fullMap.get("id"));
            minimalInfo.put("name", fullMap.get("name"));
            if (fullMap.containsKey("resourceName")) {
                minimalInfo.put("name", fullMap.get("resourceName"));
            }
        }
        auditLog.setOldValues(minimalInfo);
        auditLog.setSeverity(AuditSeverity.WARNING);
        logAction(auditLog);
    }

    public void logRead(AuditResourceType resourceType, String resourceId, String resourceName, boolean isSensitive) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.READ, resourceType, resourceId, resourceName);
        auditLog.setSensitive(isSensitive);
        auditLog.setSeverity(isSensitive ? AuditSeverity.INFO : AuditSeverity.INFO);
        logAction(auditLog);
    }

    public void logAuthentication(String action, String userId, String userEmail, boolean success, String error) {
        AuditLog auditLog = AuditLog.builder()
                .actionType(success ? AuditActionType.LOGIN : AuditActionType.LOGIN_FAILED)
                .resourceType(AuditResourceType.USER)
                .resourceId(userId)
                .resourceName(userEmail)
                .userId(userId)
                .userEmail(userEmail)
                .status(success ? AuditStatus.SUCCESS : AuditStatus.FAILURE)
                .errorMessage(error)
                .severity(success ? AuditSeverity.INFO : AuditSeverity.WARNING)
                .ipAddress(requestContextExtractor.getIpAddress())
                .userAgent(requestContextExtractor.getUserAgent())
                .requestId(requestContextExtractor.getRequestId())
                .timestamp(LocalDateTime.now())
                .build();

        if (error != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("error", error);
            auditLog.setMetadata(metadata);
        }

        logAction(auditLog);
    }

    public void logLogout(String userId, String userEmail) {
        AuditLog auditLog = AuditLog.builder()
                .actionType(AuditActionType.LOGOUT)
                .resourceType(AuditResourceType.USER)
                .resourceId(userId)
                .resourceName(userEmail)
                .userId(userId)
                .userEmail(userEmail)
                .status(AuditStatus.SUCCESS)
                .severity(AuditSeverity.INFO)
                .ipAddress(requestContextExtractor.getIpAddress())
                .userAgent(requestContextExtractor.getUserAgent())
                .requestId(requestContextExtractor.getRequestId())
                .timestamp(LocalDateTime.now())
                .build();

        logAction(auditLog);
    }

    public void logAuthorizationFailure(String resourceType, String resourceId, String reason) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.AUTHORIZATION_DENIED, 
                AuditResourceType.valueOf(resourceType), resourceId, resourceId);
        auditLog.setStatus(AuditStatus.FAILURE);
        auditLog.setSeverity(AuditSeverity.WARNING);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reason", reason);
        auditLog.setMetadata(metadata);
        logAction(auditLog);
    }

    public void logConnectionTest(AuditResourceType resourceType, String resourceId, String resourceName, boolean success, String error) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.CONNECTION_TEST, resourceType, resourceId, resourceName);
        auditLog.setStatus(success ? AuditStatus.SUCCESS : AuditStatus.FAILURE);
        auditLog.setSeverity(success ? AuditSeverity.INFO : AuditSeverity.ERROR);
        if (error != null) {
            auditLog.setErrorMessage(error);
        }
        logAction(auditLog);
    }

    public void logMfaSetupInitiated(String userId, String userEmail) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.MFA_SETUP_INITIATED, AuditResourceType.USER, userId, userEmail);
        auditLog.setStatus(AuditStatus.SUCCESS);
        auditLog.setSeverity(AuditSeverity.INFO);
        logAction(auditLog);
    }

    public void logMfaSetupCompleted(String userId, String userEmail) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.MFA_SETUP_COMPLETED, AuditResourceType.USER, userId, userEmail);
        auditLog.setStatus(AuditStatus.SUCCESS);
        auditLog.setSeverity(AuditSeverity.INFO);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("mfaType", "TOTP");
        auditLog.setMetadata(metadata);
        logAction(auditLog);
    }

    public void logMfaDisabled(String userId, String userEmail) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.MFA_DISABLED, AuditResourceType.USER, userId, userEmail);
        auditLog.setStatus(AuditStatus.SUCCESS);
        auditLog.setSeverity(AuditSeverity.WARNING);
        logAction(auditLog);
    }

    public void logMfaVerification(String userId, String userEmail, boolean success, String method, String error) {
        AuditLog auditLog = buildBaseAuditLog(
                success ? AuditActionType.MFA_VERIFICATION_SUCCESS : AuditActionType.MFA_VERIFICATION_FAILED,
                AuditResourceType.USER, userId, userEmail);
        auditLog.setStatus(success ? AuditStatus.SUCCESS : AuditStatus.FAILURE);
        auditLog.setSeverity(success ? AuditSeverity.INFO : AuditSeverity.WARNING);
        if (error != null) {
            auditLog.setErrorMessage(error);
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("method", method); // "TOTP" or "BACKUP_CODE"
        if (error != null) {
            metadata.put("error", error);
        }
        auditLog.setMetadata(metadata);
        logAction(auditLog);
    }

    public void logMfaBackupCodesRegenerated(String userId, String userEmail) {
        AuditLog auditLog = buildBaseAuditLog(AuditActionType.MFA_BACKUP_CODES_REGENERATED, AuditResourceType.USER, userId, userEmail);
        auditLog.setStatus(AuditStatus.SUCCESS);
        auditLog.setSeverity(AuditSeverity.INFO);
        logAction(auditLog);
    }

    private AuditLog buildBaseAuditLog(AuditActionType actionType, AuditResourceType resourceType, 
                                       String resourceId, String resourceName) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .actionType(actionType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .timestamp(LocalDateTime.now())
                .ipAddress(requestContextExtractor.getIpAddress())
                .userAgent(requestContextExtractor.getUserAgent())
                .requestId(requestContextExtractor.getRequestId());

        // Try to get user context
        try {
            User currentUser = authorizationService.getCurrentUser();
            builder.userId(currentUser.getId())
                   .userEmail(currentUser.getEmail())
                   .userRole(currentUser.getRole() != null ? currentUser.getRole().name() : null)
                   .organizationId(currentUser.getOrganizationId());
        } catch (Exception e) {
            // User context not available
        }

        return builder.build();
    }

    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            log.warn("Failed to convert object to map for audit log: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Extract only relevant, non-sensitive fields from an object for audit logging.
     * Excludes internal/transient fields and masks sensitive data.
     * 
     * CRITICAL SECURITY: Never logs full API keys or other sensitive credentials.
     */
    private Map<String, Object> extractRelevantFields(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        
        // Special handling for ApiKeyGenerationResult - only extract apiKey, never fullKey
        String className = obj.getClass().getSimpleName();
        if (className.contains("ApiKeyGenerationResult") || className.contains("ApiKeyGeneration")) {
            try {
                // Try to extract only the apiKey field, ignore fullKey completely
                java.lang.reflect.Method getApiKeyMethod = obj.getClass().getMethod("getApiKey");
                Object apiKeyObj = getApiKeyMethod.invoke(obj);
                if (apiKeyObj != null) {
                    // Recursively extract fields from the apiKey object only
                    return extractRelevantFields(apiKeyObj);
                }
            } catch (Exception e) {
                log.debug("Could not extract apiKey from ApiKeyGenerationResult: {}", e.getMessage());
                // Fall through to normal processing
            }
        }
        
        Map<String, Object> fullMap = convertToMap(obj);
        Map<String, Object> relevantFields = new HashMap<>();
        
        // Only include relevant fields, excluding internal/transient data
        for (Map.Entry<String, Object> entry : fullMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Skip excluded fields (including fullKey)
            if (EXCLUDED_FIELDS.contains(key)) {
                continue;
            }
            
            // Skip null values
            if (value == null) {
                continue;
            }
            
            // Skip collections and complex nested objects (except simple maps)
            if (value instanceof List || value instanceof Set) {
                continue;
            }
            
            // For maps (like properties), only include non-sensitive keys
            if (value instanceof Map) {
                Map<String, Object> mapValue = (Map<String, Object>) value;
                if (mapValue.isEmpty()) {
                    continue;
                }
                // Only store map size or key names, not values (which may contain sensitive data)
                relevantFields.put(key + "_keys", mapValue.keySet());
                continue;
            }
            
            // Include the field
            relevantFields.put(key, value);
        }
        
        // Mask sensitive fields (double-check for any that slipped through)
        maskSensitiveFields(relevantFields);
        
        return relevantFields;
    }

    private List<String> identifyChangedFields(Object oldValue, Object newValue) {
        if (oldValue == null || newValue == null) {
            return new ArrayList<>();
        }

        Map<String, Object> oldMap = convertToMap(oldValue);
        Map<String, Object> newMap = convertToMap(newValue);

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(oldMap.keySet());
        allKeys.addAll(newMap.keySet());

        return allKeys.stream()
                .filter(key -> {
                    Object oldVal = oldMap.get(key);
                    Object newVal = newMap.get(key);
                    return !Objects.equals(oldVal, newVal);
                })
                .collect(Collectors.toList());
    }

    private void maskSensitiveData(AuditLog auditLog) {
        if (auditLog.getOldValues() != null) {
            maskSensitiveFields(auditLog.getOldValues());
        }
        if (auditLog.getNewValues() != null) {
            maskSensitiveFields(auditLog.getNewValues());
        }
    }

    private void maskSensitiveFields(Map<String, Object> values) {
        if (values == null) {
            return;
        }
        
        // Mask sensitive field names (case-insensitive)
        List<String> keysToMask = new ArrayList<>();
        for (String key : values.keySet()) {
            String lowerKey = key.toLowerCase();
            for (String sensitiveField : SENSITIVE_FIELDS) {
                if (lowerKey.contains(sensitiveField.toLowerCase())) {
                    keysToMask.add(key);
                    break;
                }
            }
        }
        
        // Mask the identified fields
        for (String key : keysToMask) {
            values.put(key, "***MASKED***");
        }
        
        // Also mask common sensitive patterns in values
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                // Mask if it looks like a password/token/API key (long alphanumeric strings)
                // API keys typically look like: "brokr_<uuid>_<long-secret>" (40+ chars)
                if (strValue.length() > 20 && strValue.matches("^[A-Za-z0-9+/=_-]+$")) {
                    // Additional check: if it starts with "brokr_" it's definitely an API key
                    if (strValue.startsWith("brokr_") && strValue.length() > 40) {
                        values.put(entry.getKey(), "***API_KEY_MASKED***");
                    } else if (strValue.length() > 20 && strValue.matches("^[A-Za-z0-9+/=]+$")) {
                        values.put(entry.getKey(), "***MASKED***");
                    }
                }
            }
        }
    }

    private AuditSeverity determineSeverity(AuditLog auditLog) {
        // DELETE operations are always WARNING
        if (auditLog.getActionType() == AuditActionType.DELETE) {
            return AuditSeverity.WARNING;
        }

        // Failed operations are ERROR or WARNING
        if (auditLog.getStatus() == AuditStatus.FAILURE) {
            if (auditLog.getActionType() == AuditActionType.LOGIN_FAILED ||
                auditLog.getActionType() == AuditActionType.AUTHORIZATION_DENIED) {
                return AuditSeverity.WARNING;
            }
            return AuditSeverity.ERROR;
        }

        // CRITICAL operations
        if (auditLog.getActionType() == AuditActionType.DELETE &&
            (auditLog.getResourceType() == AuditResourceType.ORGANIZATION ||
             auditLog.getResourceType() == AuditResourceType.CLUSTER)) {
            return AuditSeverity.CRITICAL;
        }

        // Default to INFO
        return AuditSeverity.INFO;
    }
}

