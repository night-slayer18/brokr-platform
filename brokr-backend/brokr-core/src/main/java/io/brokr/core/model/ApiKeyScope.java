package io.brokr.core.model;

/**
 * API Key permission scopes for fine-grained access control.
 * Each scope grants permission to perform specific operations.
 */
public enum ApiKeyScope {
    // Cluster scopes
    CLUSTERS_READ("clusters:read", "Read cluster information"),
    CLUSTERS_WRITE("clusters:write", "Create, update, and delete clusters"),
    
    // Topic scopes
    TOPICS_READ("topics:read", "Read topic information"),
    TOPICS_WRITE("topics:write", "Create, update, and delete topics"),
    
    // Message scopes
    MESSAGES_READ("messages:read", "Read messages from topics"),
    MESSAGES_WRITE("messages:write", "Publish messages to topics"),
    
    // Consumer group scopes
    CONSUMER_GROUPS_READ("consumer-groups:read", "Read consumer group information"),
    CONSUMER_GROUPS_WRITE("consumer-groups:write", "Reset offsets and manage consumer groups"),
    
    // Metrics scopes
    METRICS_READ("metrics:read", "Read metrics (topics, consumer groups, clusters)"),
    
    // Replay scopes
    REPLAY_READ("replay:read", "Read replay job information"),
    REPLAY_WRITE("replay:write", "Create, schedule, and manage replay jobs"),
    
    // Schema Registry scopes
    SCHEMA_REGISTRY_READ("schema-registry:read", "Read schema registry information"),
    SCHEMA_REGISTRY_WRITE("schema-registry:write", "Create, update, and delete schemas"),
    
    // Kafka Connect scopes
    KAFKA_CONNECT_READ("kafka-connect:read", "Read Kafka Connect information"),
    KAFKA_CONNECT_WRITE("kafka-connect:write", "Create, update, and delete connectors"),
    
    // Kafka Streams scopes
    KAFKA_STREAMS_READ("kafka-streams:read", "Read Kafka Streams application information"),
    KAFKA_STREAMS_WRITE("kafka-streams:write", "Create, update, and delete streams applications"),
    
    // ksqlDB scopes
    KSQLDB_READ("ksqldb:read", "Read ksqlDB information and query history"),
    KSQLDB_WRITE("ksqldb:write", "Execute queries and manage streams/tables");
    
    private final String value;
    private final String description;
    
    ApiKeyScope(String value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public String getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Parse scope from string value.
     * @param value The scope string (e.g., "clusters:read")
     * @return The ApiKeyScope enum, or null if not found
     */
    public static ApiKeyScope fromValue(String value) {
        for (ApiKeyScope scope : values()) {
            if (scope.value.equals(value)) {
                return scope;
            }
        }
        return null;
    }
    
    /**
     * Check if a scope string is valid.
     * @param value The scope string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        return fromValue(value) != null;
    }
}

