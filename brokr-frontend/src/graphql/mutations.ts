import {gql} from '@apollo/client'

export const LOGIN_MUTATION = gql`
    mutation Login($input: LoginInput!) {
        login(input: $input) {
            token
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