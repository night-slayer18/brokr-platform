import type { Environment } from '@/types';
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

export interface GetOrganizationsQuery {
    organizations: {
        id: string;
        name: string;
    }[];
}

export interface GetOrganizationQuery {
    organization: {
        id: string;
        name: string;
    };
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

export interface GetEnvironmentsByOrganizationQuery {
    environments: Environment[]
}

export interface GetEnvironmentsByOrganizationVariables {
    organizationId: string
}

export interface GetOrganizationsVariables {
    // No variables needed for this query
}

export interface GetOrganizationVariables {
    id: string;
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
    id: string
}

export interface TestKafkaConnectConnectionMutation {
    testKafkaConnectConnection: boolean
}

export interface CreateKafkaStreamsApplicationMutation {
    createKafkaStreamsApplication: KafkaStreamsApplication
}

export interface UpdateKafkaStreamsApplicationMutation {
    updateKafkaStreamsApplication: KafkaStreamsApplication
}

export interface DeleteKafkaStreamsApplicationMutation {
    id: string
}

export interface SchemaRegistryInput {
    name: string
    url: string
    clusterId: string
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    username?: string
    password?: string
    isActive?: boolean
}

export interface KafkaConnectInput {
    name: string
    url: string
    clusterId: string
    securityProtocol?: 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL'
    username?: string
    password?: string
    isActive?: boolean
}

export interface KafkaStreamsApplicationInput {
    name: string
    applicationId: string
    clusterId: string
    topics?: string[]
    configuration?: Record<string, any>
    isActive?: boolean
}

export interface GetSchemaRegistryVariables {
    id: string
}

export interface GetSchemaRegistrySubjectsVariables {
    schemaRegistryId: string
}

export interface GetSchemaRegistryLatestSchemaVariables {
    schemaRegistryId: string
    subject: string
}

export interface GetSchemaRegistrySchemaVersionsVariables {
    schemaRegistryId: string
    subject: string
}

export interface GetKafkaConnectVariables {
    id: string
}

export interface GetKafkaStreamsApplicationVariables {
    id: string
}

export interface CreateSchemaRegistryMutationVariables {
    input: SchemaRegistryInput
}

export interface UpdateSchemaRegistryMutationVariables {
    id: string
    input: SchemaRegistryInput
}

export interface DeleteSchemaRegistryMutationVariables {
    id: string
}

export interface TestSchemaRegistryConnectionMutationVariables {
    id: string
}

export interface CreateKafkaConnectMutationVariables {
    input: KafkaConnectInput
}

export interface UpdateKafkaConnectMutationVariables {
    id: string
    input: KafkaConnectInput
}

export interface DeleteKafkaConnectMutationVariables {
    id: string
}

export interface TestKafkaConnectConnectionMutationVariables {
    id: string
}

export interface CreateKafkaStreamsApplicationMutationVariables {
    input: KafkaStreamsApplicationInput
}

export interface UpdateKafkaStreamsApplicationMutationVariables {
    id: string
    input: KafkaStreamsApplicationInput
}

export interface DeleteKafkaStreamsApplicationMutationVariables {
    id: string
}