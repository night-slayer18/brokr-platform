package io.brokr.core.model;

public enum AuditActionType {
    CREATE,
    UPDATE,
    DELETE,
    READ,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    AUTHORIZATION_DENIED,
    AUTHORIZATION_GRANTED,
    CONNECTION_TEST,
    CONFIGURATION_CHANGE,
    BULK_OPERATION,
    EXPORT,
    IMPORT
}

