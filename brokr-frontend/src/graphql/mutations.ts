import gql from 'graphql-tag'

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
                mfaEnabled
                mfaType
            }
            mfaRequired
            mfaType
        }
    }
`

export const VERIFY_MFA_CODE_MUTATION = gql`
    mutation VerifyMfaCode($challengeToken: String!, $code: String!, $isBackupCode: Boolean) {
        verifyMfaCode(challengeToken: $challengeToken, code: $code, isBackupCode: $isBackupCode) {
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
                mfaEnabled
                mfaType
            }
            mfaRequired
            mfaType
        }
    }
`

export const SETUP_MFA_MUTATION = gql`
    mutation SetupMfa {
        setupMfa {
            secretKey
            qrCodeDataUrl
            qrCodeUri
            deviceId
        }
    }
`

export const VERIFY_MFA_SETUP_MUTATION = gql`
    mutation VerifyMfaSetup($deviceId: String!, $code: String!) {
        verifyMfaSetup(deviceId: $deviceId, code: $code) {
            backupCodes
        }
    }
`

export const DISABLE_MFA_MUTATION = gql`
    mutation DisableMfa($password: String!) {
        disableMfa(password: $password)
    }
`

export const REGENERATE_BACKUP_CODES_MUTATION = gql`
    mutation RegenerateBackupCodes($password: String!) {
        regenerateBackupCodes(password: $password)
    }
`

export const UPDATE_ORGANIZATION_MFA_POLICY_MUTATION = gql`
    mutation UpdateOrganizationMfaPolicy($organizationId: ID!, $input: OrganizationMfaPolicyInput!) {
        updateOrganizationMfaPolicy(organizationId: $organizationId, input: $input) {
            id
            name
            mfaRequired
            mfaGracePeriodDays
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

export const EXECUTE_KSQL_QUERY = gql`
    mutation ExecuteKsqlQuery($ksqlDBId: ID!, $input: KsqlQueryInput!) {
        executeKsqlQuery(ksqlDBId: $ksqlDBId, input: $input) {
            queryId
            columns
            rows
            executionTimeMs
            errorMessage
        }
    }
`

export const EXECUTE_KSQL_STATEMENT = gql`
    mutation ExecuteKsqlStatement($ksqlDBId: ID!, $input: KsqlQueryInput!) {
        executeKsqlStatement(ksqlDBId: $ksqlDBId, input: $input) {
            queryId
            columns
            rows
            executionTimeMs
            errorMessage
        }
    }
`

export const TERMINATE_KSQL_QUERY = gql`
    mutation TerminateKsqlQuery($ksqlDBId: ID!, $queryId: String!) {
        terminateKsqlQuery(ksqlDBId: $ksqlDBId, queryId: $queryId)
    }
`

export const CREATE_KSQL_STREAM = gql`
    mutation CreateKsqlStream($ksqlDBId: ID!, $input: KsqlQueryInput!) {
        createKsqlStream(ksqlDBId: $ksqlDBId, input: $input) {
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

export const CREATE_KSQL_TABLE = gql`
    mutation CreateKsqlTable($ksqlDBId: ID!, $input: KsqlQueryInput!) {
        createKsqlTable(ksqlDBId: $ksqlDBId, input: $input) {
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

export const DROP_KSQL_STREAM = gql`
    mutation DropKsqlStream($ksqlDBId: ID!, $streamName: String!) {
        dropKsqlStream(ksqlDBId: $ksqlDBId, streamName: $streamName)
    }
`

export const DROP_KSQL_TABLE = gql`
    mutation DropKsqlTable($ksqlDBId: ID!, $tableName: String!) {
        dropKsqlTable(ksqlDBId: $ksqlDBId, tableName: $tableName)
    }
`

export const DELETE_KSQL_QUERY_HISTORY = gql`
    mutation DeleteKsqlQueryHistory($ksqlDBId: ID!, $olderThanDays: Int!) {
        deleteKsqlQueryHistory(ksqlDBId: $ksqlDBId, olderThanDays: $olderThanDays)
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

export const REPLAY_MESSAGES_MUTATION = gql`
    mutation ReplayMessages($input: MessageReplayInput!) {
        replayMessages(input: $input) {
            id
            clusterId
            sourceTopic
            targetTopic
            consumerGroupId
            status
            createdAt
        }
    }
`

export const SCHEDULE_REPLAY_MUTATION = gql`
    mutation ScheduleReplay($input: MessageReplayInput!) {
        scheduleReplay(input: $input) {
            id
            clusterId
            sourceTopic
            targetTopic
            consumerGroupId
            status
            scheduleCron
            scheduleTimezone
            nextScheduledRun
            isRecurring
            createdAt
        }
    }
`

export const CANCEL_REPLAY_MUTATION = gql`
    mutation CancelReplay($id: ID!) {
        cancelReplay(id: $id)
    }
`

export const RETRY_REPLAY_MUTATION = gql`
    mutation RetryReplay($id: ID!) {
        retryReplay(id: $id)
    }
`

export const DELETE_REPLAY_MUTATION = gql`
    mutation DeleteReplay($id: ID!) {
        deleteReplay(id: $id)
    }
`

export const CREATE_API_KEY_MUTATION = gql`
    mutation CreateApiKey($input: ApiKeyInput!) {
        createApiKey(input: $input) {
            apiKey {
                id
                userId
                organizationId
                name
                description
                keyPrefix
                scopes
                isActive
                isRevoked
                expiresAt
                createdAt
                updatedAt
            }
            fullKey
        }
    }
`

export const UPDATE_API_KEY_MUTATION = gql`
    mutation UpdateApiKey($id: ID!, $input: ApiKeyUpdateInput!) {
        updateApiKey(id: $id, input: $input) {
            id
            userId
            organizationId
            name
            description
            keyPrefix
            scopes
            isActive
            isRevoked
            expiresAt
            lastUsedAt
            createdAt
            updatedAt
        }
    }
`

export const REVOKE_API_KEY_MUTATION = gql`
    mutation RevokeApiKey($id: ID!, $reason: String) {
        revokeApiKey(id: $id, reason: $reason)
    }
`

export const ROTATE_API_KEY_MUTATION = gql`
    mutation RotateApiKey($id: ID!, $gracePeriodDays: Int) {
        rotateApiKey(id: $id, gracePeriodDays: $gracePeriodDays) {
            apiKey {
                id
                userId
                organizationId
                name
                description
                keyPrefix
                scopes
                isActive
                isRevoked
                expiresAt
                createdAt
                updatedAt
            }
            fullKey
        }
    }
`

export const DELETE_API_KEY_MUTATION = gql`
    mutation DeleteApiKey($id: ID!) {
        deleteApiKey(id: $id)
    }
`

export const CONFIGURE_API_KEY_RATE_LIMITS_MUTATION = gql`
    mutation ConfigureApiKeyRateLimits($apiKeyId: ID!, $configs: [RateLimitConfigInput!]!) {
        configureApiKeyRateLimits(apiKeyId: $apiKeyId, configs: $configs) {
            id
            apiKeyId
            limitType
            limitValue
            windowSeconds
            createdAt
            updatedAt
        }
    }
`

export const TEST_JMX_CONNECTION_MUTATION = gql`
    mutation TestJmxConnection($clusterId: ID!) {
        testJmxConnection(clusterId: $clusterId)
    }
`