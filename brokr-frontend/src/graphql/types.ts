// GraphQL Query and Mutation Types
import type {
    ConsumerGroup,
    Environment,
    KafkaCluster,
    KafkaConnect,
    KafkaStreamsApplication,
    Message,
    SchemaRegistry,
    Topic,
    User
} from '@/types';

// Query Response Types
export interface GetMeQuery {
    me: User
}

export interface GetMfaStatusQuery {
    mfaStatus: {
        enabled: boolean
        type: string | null
        unusedBackupCodesCount: number
    }
}

export interface GetClustersQuery {
    clusters: KafkaCluster[]
}

export interface GetClusterQuery {
    cluster: KafkaCluster
}

export interface GetClusterOverviewQuery {
    cluster: {
        id: string
        name: string
        isReachable: boolean
        brokers: Array<{ id: number }> | null
        topics: Array<{ name: string }> | null
        consumerGroups: Array<{ groupId: string }> | null
    }
}

export interface GetTopicsQuery {
    topics: Topic[]
}

export interface GetTopicQuery {
    topic: Topic
}

export interface GetConsumerGroupsQuery {
    consumerGroups: ConsumerGroup[]
}

export interface GetConsumerGroupQuery {
    consumerGroup: ConsumerGroup
}

export interface GetSchemaRegistriesQuery {
    schemaRegistries: SchemaRegistry[]
}

export interface GetKafkaConnectsQuery {
    kafkaConnects: KafkaConnect[]
}

export interface GetKafkaStreamsQuery {
    kafkaStreamsApplications: KafkaStreamsApplication[]
}

export interface GetOrganizationsQuery {
    organizations: {
        id: string;
        name: string;
        description?: string | null;
        isActive: boolean;
        users?: Array<{
            id: string;
            username: string;
            email: string;
            firstName?: string | null;
            lastName?: string | null;
            role: string;
            isActive: boolean;
        }>;
        environments?: Array<{
            id: string;
            name: string;
            type: string;
        }>;
    }[];
}

export interface GetOrganizationQuery {
    organization: {
        id: string;
        name: string;
        description?: string | null;
        isActive: boolean;
        mfaRequired?: boolean;
        mfaGracePeriodDays?: number | null;
        users?: Array<{
            id: string;
            username: string;
            email: string;
            firstName?: string | null;
            lastName?: string | null;
            role: string;
            organizationId?: string | null;
            accessibleEnvironmentIds?: string[];
            isActive: boolean;
        }>;
        environments?: Array<{
            id: string;
            name: string;
            type: string;
            description?: string | null;
            isActive: boolean;
        }>;
        clusters?: Array<{
            id: string;
            name: string;
            description?: string | null;
            isActive: boolean;
        }>;
    };
}

export interface GetUsersQuery {
    users: User[];
}

export interface GetUserQuery {
    user: User;
}

export interface GetMessagesQuery {
    messages: Message[]
}

// Mutation Response Types
export interface LoginMutation {
    login: {
        token: string
        user: User | null
        mfaRequired: boolean
        mfaType: string | null
    }
}

export interface VerifyMfaCodeMutation {
    verifyMfaCode: {
        token: string
        user: User
        mfaRequired: boolean
        mfaType: string | null
    }
}

export interface SetupMfaMutation {
    setupMfa: {
        secretKey: string
        qrCodeDataUrl: string
        qrCodeUri: string
        deviceId: string
    }
}

export interface VerifyMfaSetupMutation {
    verifyMfaSetup: {
        backupCodes: string[]
    }
}

export interface CreateClusterMutation {
    createCluster: KafkaCluster
}

export interface UpdateClusterMutation {
    updateCluster: KafkaCluster
}

export interface DeleteClusterMutation {
    deleteCluster: boolean
}

export interface TestClusterConnectionMutation {
    testClusterConnection: boolean
}

export interface CreateTopicMutation {
    createTopic: Topic
}

export interface DeleteTopicMutation {
    deleteTopic: boolean
}

export interface UpdateTopicMutation {
    updateTopic: Topic
}

export interface ResetConsumerOffsetMutation {
    resetConsumerGroupOffset: boolean
}

// Mutation Input Types
export interface LoginInput {
    username: string
    password: string
}

export interface KafkaClusterInput {
    name: string
    bootstrapServers: string
    description?: string
    isActive?: boolean
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    saslMechanism?: string
    saslUsername?: string
    saslPassword?: string
    sslTruststoreLocation?: string
    sslTruststorePassword?: string
    sslKeystoreLocation?: string
    sslKeystorePassword?: string
    sslKeyPassword?: string
    organizationId: string
    environmentId: string
}

export interface TopicInput {
    name: string
    partitions: number
    replicationFactor: number
    configs?: Record<string, string>
}

export interface MessageInput {
    topic: string
    partitions?: number[]
    offset?: number | string
    limit?: number
}

// Query Variables Types
export interface GetClustersVariables {
    organizationId?: string
    environmentId?: string
}

export interface GetClusterVariables {
    id: string
}

export interface GetClusterOverviewVariables {
    id: string
}

export interface GetTopicsVariables {
    clusterId: string
}

export interface GetTopicVariables {
    clusterId: string
    name: string
}

export interface GetConsumerGroupsVariables {
    clusterId: string
}

export interface GetConsumerGroupVariables {
    clusterId: string
    groupId: string
}

export interface GetSchemaRegistriesVariables {
    clusterId: string
}

export interface GetKafkaConnectsVariables {
    clusterId: string
}

export interface GetKafkaStreamsVariables {
    clusterId: string
}

export interface GetEnvironmentsByOrganizationQuery {
    environments: Environment[]
}

export interface GetEnvironmentsByOrganizationVariables {
    organizationId: string
}

export type GetOrganizationsVariables = Record<string, never>

export interface GetOrganizationVariables {
    id: string;
}

export interface GetMessagesVariables {
    clusterId: string
    input: MessageInput
}

// Message Replay Query Types
export interface GetReplayJobsQuery {
    replayJobs: import('@/types').MessageReplayJob[]
}

export interface GetReplayJobsVariables {
    clusterId?: string | null
    status?: import('@/types').ReplayJobStatus | null
    page?: number | null
    size?: number | null
}

export interface GetReplayJobQuery {
    replayJob: import('@/types').MessageReplayJob
}

export interface GetReplayJobVariables {
    id: string
}

export interface GetReplayHistoryQuery {
    replayHistory: import('@/types').MessageReplayJobHistory[]
}

export interface GetReplayHistoryVariables {
    jobId: string
    page?: number | null
    size?: number | null
}

// Mutation Variables Types
export interface LoginMutationVariables {
    input: LoginInput
}

export interface CreateClusterMutationVariables {
    input: KafkaClusterInput
}

export interface UpdateClusterMutationVariables {
    id: string
    input: KafkaClusterInput
}

export interface DeleteClusterMutationVariables {
    id: string
}

export interface TestClusterConnectionMutationVariables {
    id: string
}

export interface CreateTopicMutationVariables {
    clusterId: string
    input: TopicInput
}

export interface DeleteTopicMutationVariables {
    clusterId: string
    name: string
}

export interface UpdateTopicMutationVariables {
    clusterId: string
    name: string
    configs: Record<string, string>
}

export interface ResetConsumerOffsetMutationVariables {
    clusterId: string
    groupId: string
    topic: string
    partition: number
    offset: number
}

// Message Replay Mutation Types
export interface ReplayMessagesMutation {
    replayMessages: import('@/types').MessageReplayJob
}

export interface ReplayMessagesMutationVariables {
    input: import('@/types').MessageReplayInput
}

export interface ScheduleReplayMutation {
    scheduleReplay: import('@/types').MessageReplayJob
}

export interface ScheduleReplayMutationVariables {
    input: import('@/types').MessageReplayInput
}

export interface CancelReplayMutation {
    cancelReplay: boolean
}

export interface CancelReplayMutationVariables {
    id: string
}

export interface RetryReplayMutation {
    retryReplay: boolean
}

export interface RetryReplayMutationVariables {
    id: string
}

export interface DeleteReplayMutation {
    deleteReplay: boolean
}

export interface DeleteReplayMutationVariables {
    id: string
}

export interface GetSchemaRegistryQuery {
    schemaRegistry: SchemaRegistry
}

export interface GetSchemaRegistrySubjectsQuery {
    schemaRegistrySubjects: string[]
}

export interface GetSchemaRegistryLatestSchemaQuery {
    schemaRegistryLatestSchema: string
}

export interface GetSchemaRegistrySchemaVersionsQuery {
    schemaRegistrySchemaVersions: number[]
}

export interface GetKafkaConnectQuery {
    kafkaConnect: KafkaConnect
}

export interface GetKafkaStreamsApplicationQuery {
    kafkaStreamsApplication: KafkaStreamsApplication
}

export interface CreateSchemaRegistryMutation {
    createSchemaRegistry: SchemaRegistry
}

export interface UpdateSchemaRegistryMutation {
    updateSchemaRegistry: SchemaRegistry
}

export interface DeleteSchemaRegistryMutation {
    deleteSchemaRegistry: boolean
}

export interface TestSchemaRegistryConnectionMutation {
    testSchemaRegistryConnection: boolean
}

export interface CreateKafkaConnectMutation {
    createKafkaConnect: KafkaConnect
}

export interface UpdateKafkaConnectMutation {
    updateKafkaConnect: KafkaConnect
}

export interface DeleteKafkaConnectMutation {
    deleteKafkaConnect: boolean
}

export interface CreateKafkaStreamsApplicationMutation {
    createKafkaStreamsApplication: KafkaStreamsApplication
}

export interface UpdateKafkaStreamsApplicationMutation {
    updateKafkaStreamsApplication: KafkaStreamsApplication
}

export interface DeleteKafkaStreamsApplicationMutation {
    deleteKafkaStreamsApplication: boolean
}

// Variable Interfaces (added missing ones for deletes/tests/detail queries)
export interface DeleteSchemaRegistryMutationVariables {
    id: string
}

export interface TestSchemaRegistryConnectionMutationVariables {
    id: string
}

export interface DeleteKafkaConnectMutationVariables {
    id: string
}

export interface TestKafkaConnectConnectionMutationVariables {
    id: string
}

export interface DeleteKafkaStreamsApplicationMutationVariables {
    id: string
}

export interface GetKafkaConnectVariables {
    id: string
}

export interface GetKafkaStreamsApplicationVariables {
    id: string
}

// Inputs for create/update operations (to support variables types)
export interface SchemaRegistryInput {
    clusterId: string
    name: string
    url: string
    isActive?: boolean
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    username?: string
    password?: string
}

export interface KafkaConnectInput {
    clusterId: string
    name: string
    url: string
    isActive?: boolean
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    username?: string
    password?: string
}

export interface KafkaStreamsApplicationInput {
    clusterId: string
    name: string
    applicationId: string
    topics?: string[]
    configuration?: Record<string, unknown>
    isActive?: boolean
}

// Variables for create mutations
export interface CreateSchemaRegistryMutationVariables {
    input: SchemaRegistryInput
}

export interface CreateKafkaConnectMutationVariables {
    input: KafkaConnectInput
}

export interface CreateKafkaStreamsApplicationMutationVariables {
    input: KafkaStreamsApplicationInput
}

// Missing mutation result types
export interface TestKafkaConnectConnectionMutation {
    testKafkaConnectConnection: boolean
}

// Organization mutation types
export interface CreateOrganizationMutation {
    createOrganization: {
        id: string;
        name: string;
        description?: string | null;
        isActive: boolean;
    };
}

export interface UpdateOrganizationMutation {
    updateOrganization: {
        id: string;
        name: string;
        description?: string | null;
        isActive: boolean;
    };
}

export interface DeleteOrganizationMutation {
    deleteOrganization: boolean;
}

export interface UpdateOrganizationMfaPolicyMutation {
    updateOrganizationMfaPolicy: {
        id: string;
        name: string;
        mfaRequired: boolean;
        mfaGracePeriodDays?: number | null;
    };
}

// User mutation types
export interface CreateUserMutation {
    createUser: User;
}

export interface UpdateUserMutation {
    updateUser: User;
}

export interface DeleteUserMutation {
    deleteUser: boolean;
}

// Organization input types
export interface OrganizationInput {
    id?: string;
    name: string;
    description?: string;
    isActive: boolean;
}

// User input types
export interface UserInput {
    id?: string;
    username: string;
    email: string;
    password?: string;
    firstName?: string;
    lastName?: string;
    role: 'VIEWER' | 'ADMIN' | 'SUPER_ADMIN' | 'SERVER_ADMIN';
    organizationId?: string;
    accessibleEnvironmentIds?: string[];
    isActive: boolean;
}

// Audit Log Types
export type AuditActionType = 
    | 'CREATE' 
    | 'UPDATE' 
    | 'DELETE' 
    | 'READ' 
    | 'LOGIN' 
    | 'LOGOUT' 
    | 'LOGIN_FAILED' 
    | 'AUTHORIZATION_DENIED' 
    | 'AUTHORIZATION_GRANTED' 
    | 'CONNECTION_TEST' 
    | 'CONFIGURATION_CHANGE' 
    | 'BULK_OPERATION' 
    | 'EXPORT' 
    | 'IMPORT'

export type AuditResourceType = 
    | 'USER' 
    | 'ORGANIZATION' 
    | 'ENVIRONMENT' 
    | 'CLUSTER' 
    | 'TOPIC' 
    | 'CONSUMER_GROUP' 
    | 'SCHEMA_REGISTRY' 
    | 'KAFKA_CONNECT' 
    | 'KAFKA_STREAMS' 
    | 'KSQLDB' 
    | 'MESSAGE' 
    | 'MESSAGE_REPLAY' 
    | 'SCHEMA' 
    | 'CONNECTOR'

export type AuditStatus = 'SUCCESS' | 'FAILURE' | 'PARTIAL'

export type AuditSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL'

export interface AuditLog {
    id: string
    timestamp: number
    userId?: string | null
    userEmail?: string | null
    userRole?: string | null
    actionType: AuditActionType
    resourceType: AuditResourceType
    resourceId?: string | null
    resourceName?: string | null
    organizationId?: string | null
    environmentId?: string | null
    clusterId?: string | null
    ipAddress?: string | null
    userAgent?: string | null
    requestId?: string | null
    oldValues?: Record<string, unknown> | null
    newValues?: Record<string, unknown> | null
    changedFields?: string[] | null
    status: AuditStatus
    errorMessage?: string | null
    metadata?: Record<string, unknown> | null
    severity: AuditSeverity
    isSensitive: boolean
}

export interface AuditLogPage {
    content: AuditLog[]
    totalElements: number
    totalPages: number
    currentPage: number
    pageSize: number
}

export interface ActionTypeCount {
    actionType: AuditActionType
    count: number
}

export interface ResourceTypeCount {
    resourceType: AuditResourceType
    count: number
}

export interface StatusCount {
    status: AuditStatus
    count: number
}

export interface SeverityCount {
    severity: AuditSeverity
    count: number
}

export interface RecentActivity {
    timestamp: number
    actionType: AuditActionType
    resourceType: AuditResourceType
    resourceName: string
    userEmail: string
}

export interface AuditLogStatistics {
    totalCount: number
    byActionType: ActionTypeCount[]
    byResourceType: ResourceTypeCount[]
    byStatus: StatusCount[]
    bySeverity: SeverityCount[]
    recentActivity: RecentActivity[]
}

export interface AuditLogFilter {
    userId?: string
    actionType?: AuditActionType
    resourceType?: AuditResourceType
    resourceId?: string
    organizationId?: string
    clusterId?: string
    status?: AuditStatus
    severity?: AuditSeverity
    startTime?: number
    endTime?: number
    searchText?: string
}

export interface AuditLogPagination {
    page?: number
    size?: number
    sortBy?: string
    sortDirection?: 'ASC' | 'DESC'
}

// Audit Log Query Types
export interface GetAuditLogsQuery {
    auditLogs: AuditLogPage
}

export interface GetAuditLogQuery {
    auditLog: AuditLog
}

export interface GetAuditLogsByUserQuery {
    auditLogsByUser: AuditLogPage
}

export interface GetAuditLogsByResourceQuery {
    auditLogsByResource: AuditLogPage
}

export interface GetAuditLogStatisticsQuery {
    auditLogStatistics: AuditLogStatistics
}

// Audit Log Query Variables
export interface GetAuditLogsVariables {
    filter?: AuditLogFilter | null
    pagination?: AuditLogPagination
}

export interface GetAuditLogVariables {
    id: string
}

export interface GetAuditLogsByUserVariables {
    userId: string
    pagination?: AuditLogPagination
}

export interface GetAuditLogsByResourceVariables {
    resourceType: AuditResourceType
    resourceId: string
    pagination?: AuditLogPagination
}

export interface GetAuditLogStatisticsVariables {
    filter?: AuditLogFilter | null
}
