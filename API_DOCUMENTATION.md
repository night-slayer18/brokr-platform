# Brokr Platform API Key Documentation

Complete reference for using **API Keys** to access all Brokr Platform REST and GraphQL APIs programmatically.

---

## Quick Start

### 1. Get Your API Key

Create an API key from the Brokr UI (API Keys page) or via the API itself.

### 2. Use the API Key

Include your API key in the `Authorization` header:

```bash
Authorization: Bearer brokr_your-api-key-here
```

### 3. Make Requests

Works with both REST and GraphQL:

```bash
# REST
curl -H "Authorization: Bearer brokr_your-api-key" \
  https://your-instance.com/api/v1/brokr/clusters

# GraphQL
curl -X POST https://your-instance.com/graphql \
  -H "Authorization: Bearer brokr_your-api-key" \
  -H "Content-Type: application/json" \
  -d '{"query": "{ clusters { id name } }"}'
```

---

## API Key Format

All API keys use the format: `brokr_&lt;uuid&gt;`

Example: `brokr_550e8400-e29b-41d4-a716-446655440000`

---

## Available Scopes

When creating an API key, assign these scopes based on what operations you need:

| Scope | Description |
|-------|-------------|
| `clusters:read` | View clusters and configuration |
| `clusters:write` | Create, update, delete clusters |
| `topics:read` | View topics and configuration |
| `topics:write` | Create, update, delete topics |
| `messages:read` | Read messages from topics |
| `messages:write` | Produce messages to topics |
| `consumer-groups:read` | View consumer groups and lag |
| `consumer-groups:write` | Reset offsets, manage groups |
| `metrics:read` | Access metrics and analytics |
| `replay:read` | View replay jobs |
| `replay:write` | Create, cancel, retry replay jobs |
| `schema-registry:read` | View schemas and subjects |
| `schema-registry:write` | Register, update schemas |
| `kafka-connect:read` | View connectors |
| `kafka-connect:write` | Manage connectors |
| `kafka-streams:read` | View Kafka Streams apps |
| `kafka-streams:write` | Manage Kafka Streams apps |
| `ksqldb:read` | Execute read queries |
| `ksqldb:write` | Execute DDL/DML statements |

---

## REST API Endpoints

Base URL: `/api/v1/brokr`

All examples use: `Authorization: Bearer brokr_your-api-key`

---

### API Key Management

```
GET    /api/v1/brokr/api-keys                      # List your API keys
GET    /api/v1/brokr/api-keys/{id}                 # Get API key details
POST   /api/v1/brokr/api-keys                      # Create new API key
PUT    /api/v1/brokr/api-keys/{id}                 # Update API key
POST   /api/v1/brokr/api-keys/{id}/rotate          # Rotate API key secret
POST   /api/v1/brokr/api-keys/{id}/revoke          # Revoke API key
DELETE /api/v1/brokr/api-keys/{id}                 # Delete API key
GET    /api/v1/brokr/api-keys/{id}/usage           # Get usage statistics
GET    /api/v1/brokr/api-keys/{id}/rate-limits     # Get rate limits
PUT    /api/v1/brokr/api-keys/{id}/rate-limits     # Configure rate limits
```

---

### Clusters

```
GET    /api/v1/brokr/clusters                      # List clusters
GET    /api/v1/brokr/clusters/{id}                 # Get cluster details
POST   /api/v1/brokr/clusters                      # Create cluster
PUT    /api/v1/brokr/clusters/{id}                 # Update cluster
DELETE /api/v1/brokr/clusters/{id}                 # Delete cluster
POST   /api/v1/brokr/clusters/{id}/test-connection # Test cluster connection
POST   /api/v1/brokr/clusters/{id}/test-jmx-connection # Test JMX connection
```

---

### Topics

```
GET    /api/v1/brokr/clusters/{clusterId}/topics                    # List topics (paginated)
GET    /api/v1/brokr/clusters/{clusterId}/topics/{topicName}        # Get topic details
POST   /api/v1/brokr/clusters/{clusterId}/topics                    # Create topic
PUT    /api/v1/brokr/clusters/{clusterId}/topics/{topicName}/config # Update topic config
DELETE /api/v1/brokr/clusters/{clusterId}/topics/{topicName}        # Delete topic
```

---

### Consumer Groups

```
GET    /api/v1/brokr/clusters/{clusterId}/consumer-groups                          # List consumer groups
GET    /api/v1/brokr/clusters/{clusterId}/consumer-groups/{groupId}                # Get consumer group details
POST   /api/v1/brokr/clusters/{clusterId}/consumer-groups/{groupId}/reset-offset   # Reset offset
```

---

### Messages

```
POST   /api/v1/brokr/clusters/{clusterId}/messages    # Read messages from topic
```

Request body:
```json
{
  "topic": "my-topic",
  "partitions": [0, 1, 2],
  "offset": "earliest",
  "limit": 100
}
```

---

### Metrics

```
GET    /api/v1/brokr/metrics/topics/{clusterId}/{topicName}          # Topic metrics
GET    /api/v1/brokr/metrics/consumer-groups/{clusterId}/{groupId}   # Consumer group metrics
GET    /api/v1/brokr/metrics/clusters/{clusterId}                    # Cluster metrics
GET    /api/v1/brokr/metrics/brokers/{clusterId}                     # All broker metrics
GET    /api/v1/brokr/metrics/brokers/{clusterId}/{brokerId}          # Specific broker metrics
GET    /api/v1/brokr/metrics/brokers/{clusterId}/latest              # Latest broker metrics
```

Query params: `startTime`, `endTime`, `limit`

---

### Message Replay

```
GET    /api/v1/brokr/replay/jobs                    # List replay jobs
GET    /api/v1/brokr/replay/jobs/{id}               # Get replay job details
GET    /api/v1/brokr/replay/jobs/{id}/history       # Get replay job history
POST   /api/v1/brokr/replay/jobs                    # Create replay job
POST   /api/v1/brokr/replay/jobs/schedule           # Schedule replay job
POST   /api/v1/brokr/replay/jobs/{id}/cancel        # Cancel replay job
POST   /api/v1/brokr/replay/jobs/{id}/retry         # Retry replay job
DELETE /api/v1/brokr/replay/jobs/{id}               # Delete replay job
```

---

### Schema Registry

```
GET    /api/v1/brokr/clusters/{clusterId}/schema-registries              # List for cluster
GET    /api/v1/brokr/schema-registries/{id}                              # Get details
POST   /api/v1/brokr/schema-registries                                   # Create
PUT    /api/v1/brokr/schema-registries/{id}                              # Update
DELETE /api/v1/brokr/schema-registries/{id}                              # Delete
GET    /api/v1/brokr/schema-registries/{id}/subjects                     # List subjects
GET    /api/v1/brokr/schema-registries/{id}/subjects/{subject}/versions  # Schema versions
GET    /api/v1/brokr/schema-registries/{id}/subjects/{subject}/versions/latest # Latest schema
```

---

### Kafka Connect

```
GET    /api/v1/brokr/clusters/{clusterId}/kafka-connects    # List for cluster
GET    /api/v1/brokr/kafka-connects/{id}                    # Get details
POST   /api/v1/brokr/kafka-connects                         # Create
PUT    /api/v1/brokr/kafka-connects/{id}                    # Update
DELETE /api/v1/brokr/kafka-connects/{id}                    # Delete
```

---

### Kafka Streams

```
GET    /api/v1/brokr/clusters/{clusterId}/kafka-streams     # List for cluster
GET    /api/v1/brokr/kafka-streams/{id}                     # Get details
POST   /api/v1/brokr/kafka-streams                          # Create
PUT    /api/v1/brokr/kafka-streams/{id}                     # Update
DELETE /api/v1/brokr/kafka-streams/{id}                     # Delete
```

---

### Organizations

```
GET    /api/v1/brokr/organizations                  # List organizations
GET    /api/v1/brokr/organizations/{id}             # Get organization details
POST   /api/v1/brokr/organizations                  # Create organization
PUT    /api/v1/brokr/organizations/{id}             # Update organization
DELETE /api/v1/brokr/organizations/{id}             # Delete organization
```

---

### Environments

```
GET    /api/v1/brokr/organizations/{orgId}/environments    # List for organization
GET    /api/v1/brokr/environments/{id}                     # Get details
POST   /api/v1/brokr/environments                          # Create
PUT    /api/v1/brokr/environments/{id}                     # Update
DELETE /api/v1/brokr/environments/{id}                     # Delete
```

---

### Users

```
GET    /api/v1/brokr/users/me                       # Get current user
GET    /api/v1/brokr/users                          # List users (paginated)
GET    /api/v1/brokr/users/{id}                     # Get user details
POST   /api/v1/brokr/users                          # Create user
PUT    /api/v1/brokr/users/{id}                     # Update user
DELETE /api/v1/brokr/users/{id}                     # Delete user
```

---

### Audit Logs

```
GET    /api/v1/brokr/audit-logs                                # List audit logs (paginated, filterable)
GET    /api/v1/brokr/audit-logs/{id}                           # Get audit log details
GET    /api/v1/brokr/audit-logs/users/{userId}                 # Logs by user
GET    /api/v1/brokr/audit-logs/resources/{type}/{resourceId}  # Logs by resource
GET    /api/v1/brokr/audit-logs/statistics                     # Audit log statistics
```

---

### MFA

```
GET    /api/v1/brokr/mfa/status                     # Get MFA status
POST   /api/v1/brokr/mfa/setup                      # Initiate MFA setup
POST   /api/v1/brokr/mfa/verify-setup               # Verify MFA setup
POST   /api/v1/brokr/mfa/disable                    # Disable MFA
POST   /api/v1/brokr/mfa/backup-codes/regenerate    # Regenerate backup codes
```

---

## GraphQL API

Endpoint: `POST /graphql`

All GraphQL requests use the same authentication header:

```bash
curl -X POST https://your-instance.com/graphql \
  -H "Authorization: Bearer brokr_your-api-key" \
  -H "Content-Type: application/json" \
  -d '{"query": "YOUR_QUERY_HERE"}'
```

---

### GraphQL Queries

#### API Keys
```graphql
apiKeys                                           # List all keys
apiKey(id: ID!)                                  # Get key details
apiKeyUsage(id: ID!, startTime: String!, endTime: String!)  # Usage stats
apiKeyRateLimits(apiKeyId: ID!)                  # Rate limits
```

#### Users
```graphql
me                                               # Current user
users(organizationId: String, page: Int, size: Int)  # List users
user(id: ID!)                                    # Get user
mfaStatus                                        # MFA status
```

#### Organizations & Environments
```graphql
organizations                                    # List organizations
organization(id: ID!)                            # Get organization
environments(organizationId: String)             # List environments
environment(id: ID!)                             # Get environment
```

#### Clusters
```graphql
clusters(organizationId: String, environmentId: String)  # List clusters
cluster(id: ID!)                                 # Get cluster details
```

#### Topics
```graphql
topics(clusterId: ID!, page: Int, size: Int, search: String)  # List topics (paginated)
topic(clusterId: ID!, name: String!)             # Get topic details
```

#### Consumer Groups
```graphql
consumerGroups(clusterId: ID!)                   # List consumer groups
consumerGroup(clusterId: ID!, groupId: String!)  # Get consumer group
```

#### Messages
```graphql
messages(clusterId: ID!, input: MessageInput!)   # Read messages
```

#### Metrics
```graphql
topicMetrics(clusterId: ID!, topicName: String!, timeRange: MetricsTimeRangeInput!, limit: Int)
consumerGroupMetrics(clusterId: ID!, consumerGroupId: String!, timeRange: MetricsTimeRangeInput!, limit: Int)
clusterMetrics(clusterId: ID!, timeRange: MetricsTimeRangeInput!, limit: Int)
brokerMetrics(clusterId: ID!, timeRange: MetricsTimeRangeInput!, limit: Int)
brokerMetricsByBroker(clusterId: ID!, brokerId: Int!, timeRange: MetricsTimeRangeInput!, limit: Int)
latestBrokerMetrics(clusterId: ID!)
```

#### Schema Registry
```graphql
schemaRegistries(clusterId: ID!)                 # List for cluster
schemaRegistry(id: ID!)                          # Get details
schemaRegistrySubjects(schemaRegistryId: ID!)    # List subjects
schemaRegistryLatestSchema(schemaRegistryId: ID!, subject: String!)  # Latest schema
schemaRegistrySchemaVersions(schemaRegistryId: ID!, subject: String!)  # Schema versions
```

#### Kafka Connect
```graphql
kafkaConnects(clusterId: ID!)                    # List for cluster
kafkaConnect(id: ID!)                            # Get details
```

#### Kafka Streams
```graphql
kafkaStreamsApplications(clusterId: ID!)         # List for cluster
kafkaStreamsApplication(id: ID!)                 # Get details
```

#### ksqlDB
```graphql
ksqlDBs(clusterId: ID!)                          # List ksqlDBs
ksqlDB(id: ID!)                                  # Get ksqlDB
ksqlDBServerInfo(ksqlDBId: ID!)                  # Server info
ksqlStreams(ksqlDBId: ID!)                       # List streams
ksqlTables(ksqlDBId: ID!)                        # List tables
ksqlStreamTable(ksqlDBId: ID!, name: String!)    # Get stream/table
ksqlQueryHistory(ksqlDBId: ID!, filter: KsqlQueryFilter, pagination: KsqlQueryPagination)
ksqlQueryHistoryById(id: ID!)                    # Get query history
ksqlQueryMetrics(queryHistoryId: ID!)            # Query metrics
```

#### Message Replay
```graphql
replayJobs(clusterId: ID, status: ReplayJobStatus, page: Int, size: Int)  # List jobs
replayJob(id: ID!)                               # Get job details
replayHistory(jobId: ID!, page: Int, size: Int)  # Job history
```

#### Audit Logs
```graphql
auditLogs(filter: AuditLogFilter, pagination: AuditLogPagination)  # List logs
auditLog(id: ID!)                                # Get log details
auditLogsByUser(userId: String!, pagination: AuditLogPagination)   # By user
auditLogsByResource(resourceType: AuditResourceType!, resourceId: String!, pagination: AuditLogPagination)
auditLogStatistics(filter: AuditLogFilter)       # Statistics
```

---

### GraphQL Mutations

#### API Keys
```graphql
createApiKey(input: ApiKeyInput!)                                    # Create key
updateApiKey(id: ID!, input: ApiKeyUpdateInput!)                    # Update key
rotateApiKey(id: ID!, gracePeriodDays: Int)                         # Rotate secret
revokeApiKey(id: ID!, reason: String)                               # Revoke key
deleteApiKey(id: ID!)                                               # Delete key
configureApiKeyRateLimits(apiKeyId: ID!, configs: [RateLimitConfigInput!]!)  # Set rate limits
```

#### Users
```graphql
createUser(input: UserInput!)                    # Create user
updateUser(id: ID!, input: UserInput!)           # Update user
deleteUser(id: ID!)                              # Delete user
```

#### MFA
```graphql
setupMfa                                         # Initiate MFA setup
verifyMfaSetup(deviceId: String!, code: String!) # Complete MFA setup
disableMfa(password: String!)                    # Disable MFA
regenerateBackupCodes(password: String!)         # Regenerate backup codes
```

#### Organizations
```graphql
createOrganization(input: OrganizationInput!)    # Create
updateOrganization(id: ID!, input: OrganizationInput!)  # Update
deleteOrganization(id: ID!)                      # Delete
updateOrganizationMfaPolicy(organizationId: ID!, input: OrganizationMfaPolicyInput!)
```

#### Environments
```graphql
createEnvironment(input: EnvironmentInput!)      # Create
updateEnvironment(id: ID!, input: EnvironmentInput!)  # Update
deleteEnvironment(id: ID!)                       # Delete
```

#### Clusters
```graphql
createCluster(input: KafkaClusterInput!)         # Create
updateCluster(id: ID!, input: KafkaClusterInput!)  # Update
deleteCluster(id: ID!)                           # Delete
testClusterConnection(id: ID!)                   # Test connection
testJmxConnection(clusterId: ID!)                # Test JMX
```

#### Topics
```graphql
createTopic(clusterId: ID!, input: TopicInput!)  # Create
updateTopic(clusterId: ID!, name: String!, configs: Map!)  # Update config
deleteTopic(clusterId: ID!, name: String!)       # Delete
```

#### Consumer Groups
```graphql
resetConsumerGroupOffset(clusterId: ID!, groupId: String!, topic: String!, partition: Int!, offset: Long!)
```

#### Schema Registry
```graphql
createSchemaRegistry(input: SchemaRegistryInput!)  # Create
updateSchemaRegistry(id: ID!, input: SchemaRegistryInput!)  # Update
deleteSchemaRegistry(id: ID!)                    # Delete
testSchemaRegistryConnection(id: ID!)            # Test connection
```

#### Kafka Connect
```graphql
createKafkaConnect(input: KafkaConnectInput!)    # Create
updateKafkaConnect(id: ID!, input: KafkaConnectInput!)  # Update
deleteKafkaConnect(id: ID!)                      # Delete
testKafkaConnectConnection(id: ID!)              # Test connection
```

#### Kafka Streams
```graphql
createKafkaStreamsApplication(input: KafkaStreamsApplicationInput!)  # Create
updateKafkaStreamsApplication(id: ID!, input: KafkaStreamsApplicationInput!)  # Update
deleteKafkaStreamsApplication(id: ID!)           # Delete
```

#### ksqlDB
```graphql
createKsqlDB(input: KsqlDBInput!)                # Create
updateKsqlDB(id: ID!, input: KsqlDBInput!)       # Update
deleteKsqlDB(id: ID!)                            # Delete
testKsqlDBConnection(id: ID!)                    # Test connection
executeKsqlQuery(ksqlDBId: ID!, input: KsqlQueryInput!)  # Execute query
executeKsqlStatement(ksqlDBId: ID!, input: KsqlQueryInput!)  # Execute DDL/DML
createKsqlStream(ksqlDBId: ID!, input: KsqlQueryInput!)  # Create stream
createKsqlTable(ksqlDBId: ID!, input: KsqlQueryInput!)   # Create table
dropKsqlStream(ksqlDBId: ID!, streamName: String!)  # Drop stream
dropKsqlTable(ksqlDBId: ID!, tableName: String!)    # Drop table
terminateKsqlQuery(ksqlDBId: ID!, queryId: String!) # Terminate query
deleteKsqlQueryHistory(ksqlDBId: ID!, olderThanDays: Int!)  # Delete history
```

#### Message Replay
```graphql
replayMessages(input: MessageReplayInput!)       # Start replay
scheduleReplay(input: MessageReplayInput!)       # Schedule replay
cancelReplay(id: ID!)                            # Cancel
retryReplay(id: ID!)                             # Retry
deleteReplay(id: ID!)                            # Delete
```

---

## Rate Limiting

Configure rate limits per API key:

```bash
curl -X PUT https://your-instance.com/api/v1/brokr/api-keys/{id}/rate-limits \
  -H "Authorization: Bearer brokr_your-api-key" \
  -H "Content-Type: application/json" \
  -d '[{"limitType": "REQUESTS_PER_MINUTE", "limitValue": 100, "windowSeconds": 60}]'
```

When rate limited:
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
{"error": "Rate limit exceeded"}
```

---

## Error Responses

| Code | Description |
|------|-------------|
| `401` | Invalid or missing API key |
| `403` | Insufficient scopes |
| `404` | Resource not found |
| `429` | Rate limit exceeded |
| `500` | Internal server error |

---

## Best Practices

1. **Use minimal scopes** - Only request scopes your integration needs
2. **Set expiration dates** - Don't create keys that never expire
3. **Rotate keys regularly** - Use the rotation feature with grace periods
4. **Monitor usage** - Check usage analytics to detect anomalies
5. **Configure rate limits** - Protect your system from runaway scripts
6. **Store securely** - Use secrets managers, never commit keys to code
