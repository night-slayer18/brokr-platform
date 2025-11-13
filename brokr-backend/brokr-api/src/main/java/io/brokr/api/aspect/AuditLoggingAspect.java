package io.brokr.api.aspect;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.service.AuditService;
import io.brokr.core.model.AuditLog;
import io.brokr.core.model.AuditStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@Order(1) // Execute before other aspects
@RequiredArgsConstructor
public class AuditLoggingAspect {

    private final AuditService auditService;

    @Around("@annotation(auditLogAnnotation)")
    public Object logAudit(ProceedingJoinPoint joinPoint, io.brokr.api.annotation.AuditLoggable auditLogAnnotation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();

        // Extract resource ID and name from method parameters
        String resourceId = extractParameterValue(args, parameters, auditLogAnnotation.resourceIdParam());
        String resourceName = extractParameterValue(args, parameters, auditLogAnnotation.resourceNameParam());

        // If resourceId is not found, try to extract from first argument (common pattern: id, input.id, etc.)
        if (resourceId == null && args.length > 0) {
            resourceId = extractResourceIdFromObject(args[0]);
        }

        // If resourceName is not found, try to extract from first argument
        if (resourceName == null && args.length > 0) {
            resourceName = extractResourceNameFromObject(args[0]);
        }

        Object result = null;
        AuditStatus status = AuditStatus.SUCCESS;
        String errorMessage = null;
        Object oldValue = null;
        Object newValue = null;

        try {
            // For UPDATE operations, try to get old value before update
            if (auditLogAnnotation.action() == io.brokr.core.model.AuditActionType.UPDATE && args.length > 0) {
                oldValue = extractOldValue(args[0], resourceId);
            }

            // Execute the method
            result = joinPoint.proceed();

            // Extract new value from result
            if (auditLogAnnotation.logResult() && result != null) {
                newValue = result;
            } else if (args.length > 0) {
                newValue = args[0];
            }

            // Log success
            logAuditEvent(auditLogAnnotation, resourceId, resourceName, oldValue, newValue, status, null);

        } catch (Throwable e) {
            status = AuditStatus.FAILURE;
            errorMessage = e.getMessage();
            logAuditEvent(auditLogAnnotation, resourceId, resourceName, oldValue, null, status, errorMessage);
            throw e; // Re-throw the exception
        }

        return result;
    }

    private void logAuditEvent(AuditLoggable auditLogAnnotation, String resourceId,
                               String resourceName, Object oldValue, Object newValue,
                               AuditStatus status, String errorMessage) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .actionType(auditLogAnnotation.action())
                    .resourceType(auditLogAnnotation.resourceType())
                    .resourceId(resourceId)
                    .resourceName(resourceName)
                    .status(status)
                    .errorMessage(errorMessage);

            switch (auditLogAnnotation.action()) {
                case CREATE:
                    auditService.logCreate(auditLogAnnotation.resourceType(), resourceId, resourceName, newValue);
                    break;
                case UPDATE:
                    auditService.logUpdate(auditLogAnnotation.resourceType(), resourceId, resourceName, oldValue, newValue);
                    break;
                case DELETE:
                    auditService.logDelete(auditLogAnnotation.resourceType(), resourceId, resourceName, oldValue);
                    break;
                default:
                    // For other actions, use the generic logAction
                    AuditLog auditLog = builder.build();
                    auditService.logAction(auditLog);
                    break;
            }
        } catch (Exception e) {
            // Never fail the main operation due to audit logging failure
            log.error("Failed to log audit event in aspect: {}", e.getMessage(), e);
        }
    }

    private String extractParameterValue(Object[] args, Parameter[] parameters, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName) && i < args.length) {
                Object value = args[i];
                return value != null ? value.toString() : null;
            }
        }
        return null;
    }

    private String extractResourceIdFromObject(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            // Try common patterns: id, getId(), input.id, input.getId()
            if (obj instanceof String) {
                return (String) obj;
            }

            // Try to get id field via reflection
            try {
                Method getIdMethod = obj.getClass().getMethod("getId");
                Object id = getIdMethod.invoke(obj);
                return id != null ? id.toString() : null;
            } catch (Exception e) {
                // Try to get id field directly
                try {
                    java.lang.reflect.Field idField = obj.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    Object id = idField.get(obj);
                    return id != null ? id.toString() : null;
                } catch (Exception ex) {
                    // Try input.id pattern
                    try {
                        Method getInputMethod = obj.getClass().getMethod("getInput");
                        Object input = getInputMethod.invoke(obj);
                        if (input != null) {
                            return extractResourceIdFromObject(input);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract resource ID from object: {}", e.getMessage());
        }

        return null;
    }

    private String extractResourceNameFromObject(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            // Try common patterns: name, getName(), input.name, input.getName()
            try {
                Method getNameMethod = obj.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(obj);
                return name != null ? name.toString() : null;
            } catch (Exception e) {
                // Try to get name field directly
                try {
                    java.lang.reflect.Field nameField = obj.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    Object name = nameField.get(obj);
                    return name != null ? name.toString() : null;
                } catch (Exception ex) {
                    // Try input.name pattern
                    try {
                        Method getInputMethod = obj.getClass().getMethod("getInput");
                        Object input = getInputMethod.invoke(obj);
                        if (input != null) {
                            return extractResourceNameFromObject(input);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract resource name from object: {}", e.getMessage());
        }

        return null;
    }

    private Object extractOldValue(Object newValue, String resourceId) {
        // This is a simplified version - in a real implementation, you might want to
        // fetch the old value from the database. For now, we'll just return null
        // and let the service layer handle fetching old values if needed.
        return null;
    }
}

