export const APP_NAME = 'Brokr Platform'
export const APP_VERSION = '2.0.0'

export const ROLES = {
    VIEWER: 'VIEWER',
    ADMIN: 'ADMIN',
    SUPER_ADMIN: 'SUPER_ADMIN',
    SERVER_ADMIN: 'SERVER_ADMIN',
} as const

export const ROLE_LABELS = {
    VIEWER: 'Viewer',
    ADMIN: 'Admin',
    SUPER_ADMIN: 'Super Admin',
    SERVER_ADMIN: 'Server Admin',
}

export const SECURITY_PROTOCOLS = [
    'PLAINTEXT',
    'SSL',
    'SASL_PLAINTEXT',
    'SASL_SSL',
] as const

export const ENVIRONMENT_TYPES = {
    NON_PROD_HOTFIX: 'NON_PROD_HOTFIX',
    NON_PROD_MINOR: 'NON_PROD_MINOR',
    NON_PROD_MAJOR: 'NON_PROD_MAJOR',
    PROD: 'PROD',
} as const

export const ENVIRONMENT_TYPE_LABELS = {
    NON_PROD_HOTFIX: 'Hotfix',
    NON_PROD_MINOR: 'Minor',
    NON_PROD_MAJOR: 'Major',
    PROD: 'Production',
}

// Audit Log Constants
export const AUDIT_ACTION_TYPES = [
    'CREATE',
    'UPDATE',
    'DELETE',
    'READ',
    'LOGIN',
    'LOGOUT',
    'LOGIN_FAILED',
    'AUTHORIZATION_DENIED',
    'AUTHORIZATION_GRANTED',
    'CONNECTION_TEST',
    'CONFIGURATION_CHANGE',
    'BULK_OPERATION',
    'EXPORT',
    'IMPORT',
] as const

export const AUDIT_RESOURCE_TYPES = [
    'USER',
    'ORGANIZATION',
    'ENVIRONMENT',
    'CLUSTER',
    'TOPIC',
    'CONSUMER_GROUP',
    'SCHEMA_REGISTRY',
    'KAFKA_CONNECT',
    'KAFKA_STREAMS',
    'KSQLDB',
    'MESSAGE',
    'MESSAGE_REPLAY',
    'SCHEMA',
    'CONNECTOR',
] as const

export const AUDIT_STATUSES = ['SUCCESS', 'FAILURE', 'PARTIAL'] as const

export const AUDIT_SEVERITIES = ['INFO', 'WARNING', 'ERROR', 'CRITICAL'] as const

export const STREAMS_STATES = {
    RUNNING: 'RUNNING',
    REBALANCING: 'REBALANCING',
    PENDING_SHUTDOWN: 'PENDING_SHUTDOWN',
    NOT_RUNNING: 'NOT_RUNNING',
    ERROR: 'ERROR',
} as const

export const CONNECTOR_STATES = {
    RUNNING: 'RUNNING',
    FAILED: 'FAILED',
    PAUSED: 'PAUSED',
    UNASSIGNED: 'UNASSIGNED',
    RESTARTING: 'RESTARTING',
} as const