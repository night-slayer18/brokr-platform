// src/types/index.ts
export interface User {
    id: string
    username: string
    email: string
    firstName?: string
    lastName?: string
    role: 'VIEWER' | 'ADMIN' | 'SUPER_ADMIN' | 'SERVER_ADMIN'
    organizationId?: string
    accessibleEnvironmentIds: string[]
    isActive: boolean
    mfaEnabled?: boolean
    mfaType?: string | null
}

export interface Organization {
    id: string
    name: string
    description?: string
    isActive: boolean
    environments?: Environment[]
}

export interface Environment {
    id: string
    name: string
    type: 'NON_PROD_HOTFIX' | 'NON_PROD_MINOR' | 'NON_PROD_MAJOR' | 'PROD'
    description?: string
    isActive: boolean
    organizationId: string
}

export interface KafkaCluster {
    id: string
    name: string
    bootstrapServers: string
    description?: string
    isActive: boolean
    isReachable: boolean
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    saslMechanism?: string
    saslUsername?: string
    organization: {
        id: string
        name: string
    }
    environment: {
        id: string
        name: string
    }
    lastConnectionCheck: number
    lastConnectionError?: string
    brokers?: BrokerNode[]
    topics?: Array<{ name: string }>
    consumerGroups?: Array<{ groupId: string }>
}

export interface BrokerNode {
    id: number
    host: string
    port: number
    rack?: string | null
}

export interface Topic {
    name: string
    partitions: number
    replicationFactor: number
    isInternal: boolean
    configs?: Record<string, string>
    partitionsInfo?: PartitionInfo[]
}

export interface PartitionInfo {
    id: number
    leader: number
    replicas: number[]
    isr: number[]
    size: number
    earliestOffset: number
    latestOffset: number
}

export interface ConsumerGroup {
    groupId: string
    state: string
    coordinator?: string
    topicOffsets?: Record<string, number>
    members: MemberInfo[]
}

export interface MemberInfo {
    memberId: string
    clientId: string
    host: string
    assignment: TopicPartition[]
}

export interface TopicPartition {
    topic: string
    partition: number
}

export interface SchemaRegistry {
    id: string
    name: string
    url: string
    clusterId: string
    isActive: boolean
    isReachable: boolean
    lastConnectionError?: string
    lastConnectionCheck: number
}

export interface KafkaConnect {
    id: string
    name: string
    url: string
    clusterId: string
    isActive: boolean
    isReachable: boolean
    lastConnectionError?: string
    lastConnectionCheck: number
    connectors: Connector[]
}

export interface Connector {
    name: string
    type: string
    state: 'RUNNING' | 'FAILED' | 'PAUSED' | 'UNASSIGNED' | 'RESTARTING'
    config?: string
    tasks: Task[]
}

export interface Task {
    id: number
    state: string
    workerId: string
    trace?: string
}

export interface KafkaStreamsApplication {
    id: string
    name: string
    applicationId: string
    clusterId: string
    topics: string[]
    isActive: boolean
    state: 'RUNNING' | 'REBALANCING' | 'PENDING_SHUTDOWN' | 'NOT_RUNNING' | 'ERROR'
    configuration?: Record<string, unknown>
    threads: ThreadMetadata[]
}

export interface ThreadMetadata {
    threadName: string
    threadState: string
    consumerClientId: string[]
    tasks: TaskMetadata[]
}

export interface TaskMetadata {
    taskId: number
    taskIdString: string
    topicPartitions: string[]
    taskState: string
}

export interface Message {
    partition: number
    offset: number
    timestamp: number
    key?: string | null
    value?: string | null
    headers?: Record<string, string> | null
}

// Message Replay Types
export type ReplayJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface ReplayJobProgress {
    messagesProcessed: number
    messagesTotal?: number | null
    throughput?: number | null
    estimatedTimeRemainingSeconds?: number | null
    partitionProgress?: Record<string, unknown> | null
}

export interface MessageReplayJob {
    id: string
    clusterId: string
    sourceTopic: string
    targetTopic?: string | null
    consumerGroupId?: string | null
    startOffset?: number | null
    startTimestamp?: string | null
    endOffset?: number | null
    endTimestamp?: string | null
    partitions?: number[] | null
    status: ReplayJobStatus
    progress?: ReplayJobProgress | null
    scheduleCron?: string | null
    scheduleTimezone?: string | null
    nextScheduledRun?: string | null
    isRecurring: boolean
    lastScheduledRun?: string | null
    retryCount: number
    maxRetries: number
    retryDelaySeconds: number
    createdBy: string
    createdAt: string
    startedAt?: string | null
    completedAt?: string | null
    errorMessage?: string | null
    metadata?: Record<string, unknown> | null
}

export interface MessageReplayJobHistory {
    id: string
    replayJobId: string
    action: string
    messageCount: number
    throughput?: number | null
    timestamp: string
    details?: Record<string, unknown> | null
}

export type FilterLogic = 'AND' | 'OR'

export type KeyFilterType = 'EXACT' | 'PREFIX' | 'REGEX' | 'CONTAINS'

export type ValueFilterType = 'JSON_PATH' | 'REGEX' | 'CONTAINS' | 'SIZE'

export type KeyTransformationType = 'KEEP' | 'REMOVE' | 'MODIFY'

export type ValueTransformationType = 'KEEP' | 'MODIFY' | 'FORMAT_CONVERSION'

export interface KeyFilterInput {
    type: KeyFilterType
    value: string
}

export interface ValueFilterInput {
    type: ValueFilterType
    value?: string | null
    minSize?: number | null
    maxSize?: number | null
}

export interface HeaderFilterInput {
    headerKey: string
    headerValue?: string | null
    exactMatch?: boolean | null
}

export interface TimestampRangeFilterInput {
    startTimestamp: string
    endTimestamp: string
}

export interface MessageFilterInput {
    keyFilter?: KeyFilterInput | null
    valueFilter?: ValueFilterInput | null
    headerFilters?: HeaderFilterInput[] | null
    timestampRangeFilter?: TimestampRangeFilterInput | null
    logic?: FilterLogic | null
}

export interface KeyTransformationInput {
    type: KeyTransformationType
    newValue?: string | null
}

export interface ValueTransformationInput {
    type: ValueTransformationType
    newValue?: string | null
    targetFormat?: string | null
}

export interface MessageTransformationInput {
    keyTransformation?: KeyTransformationInput | null
    valueTransformation?: ValueTransformationInput | null
    headerAdditions?: Record<string, string> | null
    headerRemovals?: string[] | null
}

export interface MessageReplayInput {
    clusterId: string
    sourceTopic: string
    targetTopic?: string | null
    consumerGroupId?: string | null
    startOffset?: number | null
    startTimestamp?: string | null
    endOffset?: number | null
    endTimestamp?: string | null
    partitions?: number[] | null
    filters?: MessageFilterInput | null
    transformation?: MessageTransformationInput | null
    scheduleTime?: string | null
    scheduleCron?: string | null
    scheduleTimezone?: string | null
    maxRetries?: number | null
    retryDelaySeconds?: number | null
}

// API Key Types
export interface ApiKey {
    id: string
    userId: string
    organizationId: string
    name: string
    description?: string | null
    keyPrefix: string
    scopes: string[]
    isActive: boolean
    isRevoked: boolean
    revokedAt?: string | null
    revokedReason?: string | null
    expiresAt?: string | null
    lastUsedAt?: string | null
    createdAt: string
    updatedAt: string
}

export interface ApiKeyGenerationResult {
    apiKey: ApiKey
    fullKey: string
}

export interface ApiKeyUsageStatistics {
    apiKeyId: string
    startTime: string
    endTime: string
    totalRequests: number
    successCount: number
    errorCount: number
    errorRate: number
    averageResponseTimeMs?: number | null
    statusCodeCounts?: Record<string, number> | null
}

export interface RateLimitConfig {
    id: string
    apiKeyId: string
    limitType: string
    limitValue: number
    windowSeconds: number
    createdAt: string
    updatedAt: string
}

export interface ApiKeyInput {
    name: string
    description?: string | null
    scopes: string[]
    expiresAt?: string | null
}

export interface ApiKeyUpdateInput {
    name?: string | null
    description?: string | null
    scopes?: string[] | null
    expiresAt?: string | null
}

export interface RateLimitConfigInput {
    limitType: string
    limitValue: number
    windowSeconds: number
}

// Available API Key Scopes
export const API_KEY_SCOPES = [
    { value: 'clusters:read', label: 'Clusters: Read', description: 'Read cluster information' },
    { value: 'clusters:write', label: 'Clusters: Write', description: 'Create and modify clusters' },
    { value: 'topics:read', label: 'Topics: Read', description: 'Read topic information and messages' },
    { value: 'topics:write', label: 'Topics: Write', description: 'Create and modify topics' },
    { value: 'messages:read', label: 'Messages: Read', description: 'Read messages from topics' },
    { value: 'messages:write', label: 'Messages: Write', description: 'Produce messages to topics' },
    { value: 'consumer-groups:read', label: 'Consumer Groups: Read', description: 'Read consumer group information' },
    { value: 'consumer-groups:write', label: 'Consumer Groups: Write', description: 'Modify consumer groups' },
    { value: 'metrics:read', label: 'Metrics: Read', description: 'Read metrics and statistics' },
    { value: 'replay:read', label: 'Replay: Read', description: 'View replay jobs' },
    { value: 'replay:write', label: 'Replay: Write', description: 'Create and manage replay jobs' },
    { value: 'schema-registry:read', label: 'Schema Registry: Read', description: 'Read schema registry information' },
    { value: 'schema-registry:write', label: 'Schema Registry: Write', description: 'Modify schema registry' },
    { value: 'kafka-connect:read', label: 'Kafka Connect: Read', description: 'Read Kafka Connect information' },
    { value: 'kafka-connect:write', label: 'Kafka Connect: Write', description: 'Modify Kafka Connect' },
    { value: 'kafka-streams:read', label: 'Kafka Streams: Read', description: 'Read Kafka Streams information' },
    { value: 'kafka-streams:write', label: 'Kafka Streams: Write', description: 'Modify Kafka Streams' },
    { value: 'ksqldb:read', label: 'ksqlDB: Read', description: 'Read ksqlDB information' },
    { value: 'ksqldb:write', label: 'ksqlDB: Write', description: 'Modify ksqlDB' },
] as const
