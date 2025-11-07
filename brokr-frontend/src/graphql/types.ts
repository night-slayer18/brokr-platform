// GraphQL Query and Mutation Types
import type {
    ConsumerGroup,
    KafkaCluster,
    KafkaConnect,
    KafkaStreamsApplication,
    SchemaRegistry,
    Topic,
    User
} from '@/types'

// Query Response Types
export interface GetMeQuery {
    me: User
}

export interface GetClustersQuery {
    clusters: KafkaCluster[]
}

export interface GetClusterQuery {
    cluster: KafkaCluster
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

// Mutation Response Types
export interface LoginMutation {
    login: {
        token: string
        user: User
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
    organizationId: string
    environmentId: string
}

export interface TopicInput {
    name: string
    partitions: number
    replicationFactor: number
    configs?: Record<string, string>
}

// Query Variables Types
export interface GetClustersVariables {
    organizationId?: string
    environmentId?: string
}

export interface GetClusterVariables {
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
