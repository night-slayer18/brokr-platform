import gql from 'graphql-tag'

export const LOGIN_MUTATION = gql`
    mutation Login($input: LoginInput!) {
        login(input: $input) {
            # Token is now in HttpOnly cookie, not returned in response (secure against XSS)
            user {
                id
                username
                email
                firstName
                lastName
                role
                organizationId
                accessibleEnvironmentIds
                isActive
            }
        }
    }
`

export const CREATE_CLUSTER_MUTATION = gql`
    mutation CreateCluster($input: KafkaClusterInput!) {
        createCluster(input: $input) {
            id
            name
            bootstrapServers
            description
            isActive
        }
    }
`

export const UPDATE_CLUSTER_MUTATION = gql`
    mutation UpdateCluster($id: ID!, $input: KafkaClusterInput!) {
        updateCluster(id: $id, input: $input) {
            id
            name
            bootstrapServers
            description
            isActive
        }
    }
`

export const DELETE_CLUSTER_MUTATION = gql`
    mutation DeleteCluster($id: ID!) {
        deleteCluster(id: $id)
    }
`

export const TEST_CLUSTER_CONNECTION_MUTATION = gql`
    mutation TestClusterConnection($id: ID!) {
        testClusterConnection(id: $id)
    }
`

export const CREATE_TOPIC_MUTATION = gql`
    mutation CreateTopic($clusterId: ID!, $input: TopicInput!) {
        createTopic(clusterId: $clusterId, input: $input) {
            name
            partitions
            replicationFactor
        }
    }
`

export const DELETE_TOPIC_MUTATION = gql`
    mutation DeleteTopic($clusterId: ID!, $name: String!) {
        deleteTopic(clusterId: $clusterId, name: $name)
    }
`

export const UPDATE_TOPIC_MUTATION = gql`
    mutation UpdateTopic($clusterId: ID!, $name: String!, $configs: Map!) {
        updateTopic(clusterId: $clusterId, name: $name, configs: $configs) {
            name
            configs
        }
    }
`

export const RESET_CONSUMER_OFFSET_MUTATION = gql`
    mutation ResetConsumerGroupOffset(
        $clusterId: ID!
        $groupId: String!
        $topic: String!
        $partition: Int!
        $offset: Long!
    ) {
        resetConsumerGroupOffset(
            clusterId: $clusterId
            groupId: $groupId
            topic: $topic
            partition: $partition
            offset: $offset
        )
    }
`

export const CREATE_SCHEMA_REGISTRY_MUTATION = gql`
    mutation CreateSchemaRegistry($input: SchemaRegistryInput!) {
        createSchemaRegistry(input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const UPDATE_SCHEMA_REGISTRY_MUTATION = gql`
    mutation UpdateSchemaRegistry($id: ID!, $input: SchemaRegistryInput!) {
        updateSchemaRegistry(id: $id, input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const DELETE_SCHEMA_REGISTRY_MUTATION = gql`
    mutation DeleteSchemaRegistry($id: ID!) {
        deleteSchemaRegistry(id: $id)
    }
`

export const TEST_SCHEMA_REGISTRY_CONNECTION_MUTATION = gql`
    mutation TestSchemaRegistryConnection($id: ID!) {
        testSchemaRegistryConnection(id: $id)
    }
`

export const CREATE_KAFKA_CONNECT_MUTATION = gql`
    mutation CreateKafkaConnect($input: KafkaConnectInput!) {
        createKafkaConnect(input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const UPDATE_KAFKA_CONNECT_MUTATION = gql`
    mutation UpdateKafkaConnect($id: ID!, $input: KafkaConnectInput!) {
        updateKafkaConnect(id: $id, input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const DELETE_KAFKA_CONNECT_MUTATION = gql`
    mutation DeleteKafkaConnect($id: ID!) {
        deleteKafkaConnect(id: $id)
    }
`

export const TEST_KAFKA_CONNECT_CONNECTION_MUTATION = gql`
    mutation TestKafkaConnectConnection($id: ID!) {
        testKafkaConnectConnection(id: $id)
    }
`

export const CREATE_KAFKA_STREAMS_APPLICATION_MUTATION = gql`
    mutation CreateKafkaStreamsApplication($input: KafkaStreamsApplicationInput!) {
        createKafkaStreamsApplication(input: $input) {
            id
            name
            applicationId
            topics
            isActive
            state
        }
    }
`

export const UPDATE_KAFKA_STREAMS_APPLICATION_MUTATION = gql`
    mutation UpdateKafkaStreamsApplication($id: ID!, $input: KafkaStreamsApplicationInput!) {
        updateKafkaStreamsApplication(id: $id, input: $input) {
            id
            name
            applicationId
            topics
            isActive
            state
        }
    }
`

export const DELETE_KAFKA_STREAMS_APPLICATION_MUTATION = gql`
    mutation DeleteKafkaStreamsApplication($id: ID!) {
        deleteKafkaStreamsApplication(id: $id)
    }
`

export const CREATE_KSQLDB_MUTATION = gql`
    mutation CreateKsqlDB($input: KsqlDBInput!) {
        createKsqlDB(input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const UPDATE_KSQLDB_MUTATION = gql`
    mutation UpdateKsqlDB($id: ID!, $input: KsqlDBInput!) {
        updateKsqlDB(id: $id, input: $input) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
        }
    }
`

export const DELETE_KSQLDB_MUTATION = gql`
    mutation DeleteKsqlDB($id: ID!) {
        deleteKsqlDB(id: $id)
    }
`

export const TEST_KSQLDB_CONNECTION_MUTATION = gql`
    mutation TestKsqlDBConnection($id: ID!) {
        testKsqlDBConnection(id: $id)
    }
`

export const CREATE_ORGANIZATION_MUTATION = gql`
    mutation CreateOrganization($input: OrganizationInput!) {
        createOrganization(input: $input) {
            id
            name
            description
            isActive
        }
    }
`

export const UPDATE_ORGANIZATION_MUTATION = gql`
    mutation UpdateOrganization($id: ID!, $input: OrganizationInput!) {
        updateOrganization(id: $id, input: $input) {
            id
            name
            description
            isActive
        }
    }
`

export const DELETE_ORGANIZATION_MUTATION = gql`
    mutation DeleteOrganization($id: ID!) {
        deleteOrganization(id: $id)
    }
`

export const CREATE_USER_MUTATION = gql`
    mutation CreateUser($input: UserInput!) {
        createUser(input: $input) {
            id
            username
            email
            firstName
            lastName
            role
            organizationId
            accessibleEnvironmentIds
            isActive
        }
    }
`

export const UPDATE_USER_MUTATION = gql`
    mutation UpdateUser($id: ID!, $input: UserInput!) {
        updateUser(id: $id, input: $input) {
            id
            username
            email
            firstName
            lastName
            role
            organizationId
            accessibleEnvironmentIds
            isActive
        }
    }
`

export const DELETE_USER_MUTATION = gql`
    mutation DeleteUser($id: ID!) {
        deleteUser(id: $id)
    }
`