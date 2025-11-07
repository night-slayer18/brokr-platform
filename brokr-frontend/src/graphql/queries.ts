import {gql} from '@apollo/client'

export const GET_ME = gql`
    query GetMe {
        me {
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

export const GET_CLUSTERS = gql`
    query GetClusters($organizationId: String, $environmentId: String) {
        clusters(organizationId: $organizationId, environmentId: $environmentId) {
            id
            name
            bootstrapServers
            description
            isActive
            isReachable
            securityProtocol
            organization {
                id
                name
            }
            environment {
                id
                name
            }
            lastConnectionCheck
            lastConnectionError
        }
    }
`

export const GET_CLUSTER = gql`
    query GetCluster($id: ID!) {
        cluster(id: $id) {
            id
            name
            bootstrapServers
            description
            isActive
            isReachable
            securityProtocol
            organization {
                id
                name
            }
            environment {
                id
                name
            }
            lastConnectionCheck
            lastConnectionError
        }
    }
`

export const GET_TOPICS = gql`
    query GetTopics($clusterId: ID!) {
        topics(clusterId: $clusterId) {
            name
            partitions
            replicationFactor
            isInternal
        }
    }
`

export const GET_TOPIC = gql`
    query GetTopic($clusterId: ID!, $name: String!) {
        topic(clusterId: $clusterId, name: $name) {
            name
            partitions
            replicationFactor
            isInternal
            configs
            partitionsInfo {
                id
                leader
                replicas
                isr
                size
                earliestOffset
                latestOffset
            }
        }
    }
`

export const GET_CONSUMER_GROUPS = gql`
    query GetConsumerGroups($clusterId: ID!) {
        consumerGroups(clusterId: $clusterId) {
            groupId
            state
            coordinator
            topicOffsets
            members {
                memberId
                clientId
                host
                assignment {
                    topic
                    partition
                }
            }
        }
    }
`

export const GET_CONSUMER_GROUP = gql`
    query GetConsumerGroup($clusterId: ID!, $groupId: String!) {
        consumerGroup(clusterId: $clusterId, groupId: $groupId) {
            groupId
            state
            coordinator
            topicOffsets
            members {
                memberId
                clientId
                host
                assignment {
                    topic
                    partition
                }
            }
        }
    }
`

export const GET_SCHEMA_REGISTRIES = gql`
    query GetSchemaRegistries($clusterId: ID!) {
        schemaRegistries(clusterId: $clusterId) {
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

export const GET_KAFKA_CONNECTS = gql`
    query GetKafkaConnects($clusterId: ID!) {
        kafkaConnects(clusterId: $clusterId) {
            id
            name
            url
            isActive
            isReachable
            lastConnectionError
            lastConnectionCheck
            connectors {
                name
                type
                state
                tasks {
                    id
                    state
                    workerId
                }
            }
        }
    }
`

export const GET_KAFKA_STREAMS = gql`
    query GetKafkaStreamsApplications($clusterId: ID!) {
        kafkaStreamsApplications(clusterId: $clusterId) {
            id
            name
            applicationId
            topics
            isActive
            state
            threads {
                threadName
                threadState
                consumerClientId
                tasks {
                    taskId
                    taskIdString
                    topicPartitions
                    taskState
                }
            }
        }
    }
`