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
            mfaEnabled
            mfaType
        }
    }
`

export const GET_MFA_STATUS = gql`
    query GetMfaStatus {
        mfaStatus {
            enabled
            type
            unusedBackupCodesCount
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
            topics {
                name
            }
            consumerGroups {
                groupId
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

export const GET_KSQL_STREAMS = gql`
    query GetKsqlStreams($ksqlDBId: ID!) {
        ksqlStreams(ksqlDBId: $ksqlDBId) {
            id
            name
            type
            topicName
            keyFormat
            valueFormat
            schema
            queryText
            createdAt
            updatedAt
        }
    }
`

export const GET_KSQL_TABLES = gql`
    query GetKsqlTables($ksqlDBId: ID!) {
        ksqlTables(ksqlDBId: $ksqlDBId) {
            id
            name
            type
            topicName
            keyFormat
            valueFormat
            schema
            queryText
            createdAt
            updatedAt
        }
    }
`

export const GET_KSQL_STREAM_TABLE = gql`
    query GetKsqlStreamTable($ksqlDBId: ID!, $name: String!) {
        ksqlStreamTable(ksqlDBId: $ksqlDBId, name: $name) {
            id
            name
            type
            topicName
            keyFormat
            valueFormat
            schema
            queryText
            createdAt
            updatedAt
        }
    }
`

export const GET_KSQL_QUERY_HISTORY = gql`
    query GetKsqlQueryHistory(
        $ksqlDBId: ID!
        $filter: KsqlQueryFilter
        $pagination: KsqlQueryPagination
    ) {
        ksqlQueryHistory(ksqlDBId: $ksqlDBId, filter: $filter, pagination: $pagination) {
            content {
                id
                queryText
                queryType
                status
                executionTimeMs
                rowsReturned
                errorMessage
                startedAt
                completedAt
                user {
                    id
                    username
                    email
                }
            }
            totalElements
            totalPages
            currentPage
            pageSize
        }
    }
`

export const GET_KSQL_QUERY_HISTORY_BY_ID = gql`
    query GetKsqlQueryHistoryById($id: ID!) {
        ksqlQueryHistoryById(id: $id) {
            id
            queryText
            queryType
            status
            executionTimeMs
            rowsReturned
            errorMessage
            startedAt
            completedAt
            user {
                id
                username
                email
            }
        }
    }
`

export const GET_KSQL_QUERY_METRICS = gql`
    query GetKsqlQueryMetrics($queryHistoryId: ID!) {
        ksqlQueryMetrics(queryHistoryId: $queryHistoryId) {
            queryHistoryId
            cpuUsagePercent
            memoryUsageMb
            rowsProcessedPerSecond
            bytesRead
            bytesWritten
            timestamp
        }
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

export const GET_REPLAY_JOBS = gql`
    query GetReplayJobs($clusterId: ID, $status: ReplayJobStatus, $page: Int, $size: Int) {
        replayJobs(clusterId: $clusterId, status: $status, page: $page, size: $size) {
            id
            clusterId
            sourceTopic
            targetTopic
            consumerGroupId
            startOffset
            startTimestamp
            endOffset
            endTimestamp
            partitions
            status
            progress {
                messagesProcessed
                messagesTotal
                throughput
                estimatedTimeRemainingSeconds
            }
            scheduleCron
            scheduleTimezone
            nextScheduledRun
            isRecurring
            lastScheduledRun
            retryCount
            maxRetries
            retryDelaySeconds
            createdBy
            createdAt
            startedAt
            completedAt
            errorMessage
        }
    }
`

export const GET_REPLAY_JOB = gql`
    query GetReplayJob($id: ID!) {
        replayJob(id: $id) {
            id
            clusterId
            sourceTopic
            targetTopic
            consumerGroupId
            startOffset
            startTimestamp
            endOffset
            endTimestamp
            partitions
            status
            progress {
                messagesProcessed
                messagesTotal
                throughput
                estimatedTimeRemainingSeconds
            }
            scheduleCron
            scheduleTimezone
            nextScheduledRun
            isRecurring
            lastScheduledRun
            retryCount
            maxRetries
            retryDelaySeconds
            createdBy
            createdAt
            startedAt
            completedAt
            errorMessage
        }
    }
`

export const GET_REPLAY_HISTORY = gql`
    query GetReplayHistory($jobId: ID!, $page: Int, $size: Int) {
        replayHistory(jobId: $jobId, page: $page, size: $size) {
            id
            replayJobId
            action
            messageCount
            throughput
            timestamp
            details
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
            mfaRequired
            mfaGracePeriodDays
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

export const GET_TOPIC_METRICS = gql`
    query GetTopicMetrics($clusterId: ID!, $topicName: String!, $timeRange: MetricsTimeRangeInput!, $limit: Int) {
        topicMetrics(clusterId: $clusterId, topicName: $topicName, timeRange: $timeRange, limit: $limit) {
            id
            clusterId
            topicName
            messagesPerSecondIn
            bytesPerSecondIn
            totalSizeBytes
            partitionCount
            partitionSizes
            partitionOffsets
            timestamp
        }
    }
`

export const GET_CONSUMER_GROUP_METRICS = gql`
    query GetConsumerGroupMetrics($clusterId: ID!, $consumerGroupId: String!, $timeRange: MetricsTimeRangeInput!, $limit: Int) {
        consumerGroupMetrics(clusterId: $clusterId, consumerGroupId: $consumerGroupId, timeRange: $timeRange, limit: $limit) {
            id
            clusterId
            consumerGroupId
            totalLag
            maxLag
            minLag
            avgLag
            totalOffset
            committedOffset
            memberCount
            activeMemberCount
            topicLags
            timestamp
        }
    }
`

export const GET_CLUSTER_METRICS = gql`
    query GetClusterMetrics($clusterId: ID!, $timeRange: MetricsTimeRangeInput!, $limit: Int) {
        clusterMetrics(clusterId: $clusterId, timeRange: $timeRange, limit: $limit) {
            id
            clusterId
            brokerCount
            activeBrokerCount
            totalTopics
            totalPartitions
            totalMessagesPerSecond
            totalBytesPerSecond
            isHealthy
            connectionErrorCount
            brokerDetails
            timestamp
        }
    }
`
