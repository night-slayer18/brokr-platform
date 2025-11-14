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
}
