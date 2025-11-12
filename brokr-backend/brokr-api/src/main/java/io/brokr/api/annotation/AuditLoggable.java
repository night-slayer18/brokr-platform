package io.brokr.api.annotation;

import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLoggable {
    AuditActionType action();
    AuditResourceType resourceType();
    String resourceIdParam() default "";  // Parameter name that contains resource ID
    String resourceNameParam() default ""; // Parameter name that contains resource name
    boolean logResult() default true;      // Whether to log the result
}

