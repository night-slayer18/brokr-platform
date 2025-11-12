import gql from 'graphql-tag'

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
            brokers {
                id
                host
                port
                rack
            }
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
            brokers {
                id
                host
                port
                rack
            }
        }
    }
`

export const GET_CLUSTER_OVERVIEW = gql`
    query GetClusterOverview($id: ID!) {
        cluster(id: $id) {
            id
            name
            isReachable
            brokers {
                id
            }
            topics {
                name
            }
            consumerGroups {
                groupId
            }
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

export const GET_SCHEMA_REGISTRY = gql`
    query GetSchemaRegistry($id: ID!) {
        schemaRegistry(id: $id) {
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

export const GET_KSQLDBS = gql`
    query GetKsqlDBs($clusterId: ID!) {
        ksqlDBs(clusterId: $clusterId) {
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

export const GET_KSQLDB = gql`
    query GetKsqlDB($id: ID!) {
        ksqlDB(id: $id) {
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

export const GET_KSQLDB_SERVER_INFO = gql`
    query GetKsqlDBServerInfo($ksqlDBId: ID!) {
        ksqlDBServerInfo(ksqlDBId: $ksqlDBId)
    }
`

export const GET_SCHEMA_REGISTRY_SUBJECTS = gql`
    query GetSchemaRegistrySubjects($schemaRegistryId: ID!) {
        schemaRegistrySubjects(schemaRegistryId: $schemaRegistryId)
    }
`

export const GET_SCHEMA_REGISTRY_LATEST_SCHEMA = gql`
    query GetSchemaRegistryLatestSchema($schemaRegistryId: ID!, $subject: String!) {
        schemaRegistryLatestSchema(schemaRegistryId: $schemaRegistryId, subject: $subject)
    }
`

export const GET_SCHEMA_REGISTRY_SCHEMA_VERSIONS = gql`
    query GetSchemaRegistrySchemaVersions($schemaRegistryId: ID!, $subject: String!) {
        schemaRegistrySchemaVersions(schemaRegistryId: $schemaRegistryId, subject: $subject)
    }
`

export const GET_KAFKA_CONNECT = gql`
    query GetKafkaConnect($id: ID!) {
        kafkaConnect(id: $id) {
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
                config
                tasks {
                    id
                    state
                    workerId
                    trace
                }
            }
        }
    }
`

export const GET_KAFKA_STREAMS_APPLICATION = gql`
    query GetKafkaStreamsApplication($id: ID!) {
        kafkaStreamsApplication(id: $id) {
            id
            name
            applicationId
            topics
            isActive
            state
            configuration
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

export const GET_MESSAGES = gql`
    query GetMessages($clusterId: ID!, $input: MessageInput!) {
        messages(clusterId: $clusterId, input: $input) {
            partition
            offset
            timestamp
            key
            value
        }
    }
`

export const GET_ENVIRONMENTS_BY_ORGANIZATION = gql`
    query GetEnvironmentsByOrganization($organizationId: String) {
        environments(organizationId: $organizationId) {
            id
            name
            type
            description
            isActive
            organization {
                id
            }
        }
    }
`

export const GET_ORGANIZATIONS = gql`
    query GetOrganizations {
        organizations {
            id
            name
            description
            isActive
            users {
                id
                username
                email
                firstName
                lastName
                role
                isActive
            }
            environments {
                id
                name
                type
            }
        }
    }
`

export const GET_ORGANIZATION = gql`
    query GetOrganization($id: ID!) {
        organization(id: $id) {
            id
            name
            description
            isActive
            users {
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
            environments {
                id
                name
                type
                description
                isActive
            }
            clusters {
                id
                name
                description
                isActive
            }
        }
    }
`

export const GET_USERS = gql`
    query GetUsers($organizationId: String) {
        users(organizationId: $organizationId) {
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

export const GET_USER = gql`
    query GetUser($id: ID!) {
        user(id: $id) {
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

export const GET_AUDIT_LOGS = gql`
    query GetAuditLogs($filter: AuditLogFilter, $pagination: AuditLogPagination) {
        auditLogs(filter: $filter, pagination: $pagination) {
            content {
                id
                timestamp
                userId
                userEmail
                userRole
                actionType
                resourceType
                resourceId
                resourceName
                organizationId
                environmentId
                clusterId
                ipAddress
                userAgent
                requestId
                oldValues
                newValues
                changedFields
                status
                errorMessage
                metadata
                severity
                isSensitive
            }
            totalElements
            totalPages
            currentPage
            pageSize
        }
    }
`

export const GET_AUDIT_LOG = gql`
    query GetAuditLog($id: ID!) {
        auditLog(id: $id) {
            id
            timestamp
            userId
            userEmail
            userRole
            actionType
            resourceType
            resourceId
            resourceName
            organizationId
            environmentId
            clusterId
            ipAddress
            userAgent
            requestId
            oldValues
            newValues
            changedFields
            status
            errorMessage
            metadata
            severity
            isSensitive
        }
    }
`

export const GET_AUDIT_LOGS_BY_USER = gql`
    query GetAuditLogsByUser($userId: String!, $pagination: AuditLogPagination) {
        auditLogsByUser(userId: $userId, pagination: $pagination) {
            content {
                id
                timestamp
                userId
                userEmail
                userRole
                actionType
                resourceType
                resourceId
                resourceName
                organizationId
                environmentId
                clusterId
                ipAddress
                userAgent
                requestId
                oldValues
                newValues
                changedFields
                status
                errorMessage
                metadata
                severity
                isSensitive
            }
            totalElements
            totalPages
            currentPage
            pageSize
        }
    }
`

export const GET_AUDIT_LOGS_BY_RESOURCE = gql`
    query GetAuditLogsByResource($resourceType: AuditResourceType!, $resourceId: String!, $pagination: AuditLogPagination) {
        auditLogsByResource(resourceType: $resourceType, resourceId: $resourceId, pagination: $pagination) {
            content {
                id
                timestamp
                userId
                userEmail
                userRole
                actionType
                resourceType
                resourceId
                resourceName
                organizationId
                environmentId
                clusterId
                ipAddress
                userAgent
                requestId
                oldValues
                newValues
                changedFields
                status
                errorMessage
                metadata
                severity
                isSensitive
            }
            totalElements
            totalPages
            currentPage
            pageSize
        }
    }
`

export const GET_AUDIT_LOG_STATISTICS = gql`
    query GetAuditLogStatistics($filter: AuditLogFilter) {
        auditLogStatistics(filter: $filter) {
            totalCount
            byActionType {
                actionType
                count
            }
            byResourceType {
                resourceType
                count
            }
            byStatus {
                status
                count
            }
            bySeverity {
                severity
                count
            }
            recentActivity {
                timestamp
                actionType
                resourceType
                resourceName
                userEmail
            }
        }
    }
`
