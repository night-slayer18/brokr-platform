package io.brokr.api.service;

import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.KsqlDB;
import io.brokr.core.model.KsqlStreamTable;
import io.brokr.kafka.service.KsqlDBService;
import io.brokr.storage.entity.KsqlDBEntity;
import io.brokr.storage.entity.KsqlStreamTableEntity;
import io.brokr.storage.repository.KsqlDBRepository;
import io.brokr.storage.repository.KsqlStreamTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KsqlDBStreamTableService {

    private final KsqlDBRepository ksqlDBRepository;
    private final KsqlStreamTableRepository streamTableRepository;
    private final KsqlDBService ksqlDBService;

    /**
     * Get all streams for a ksqlDB instance
     */
    @Transactional(readOnly = true)
    public List<KsqlStreamTable> getStreams(String ksqlDBId) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Try to fetch from ksqlDB and sync
        try {
            List<KsqlDBService.KsqlStream> ksqlStreams = ksqlDBService.getStreams(ksqlDB);
            syncStreamsTables(ksqlDBId, ksqlStreams, "STREAM");
        } catch (Exception e) {
            log.warn("Failed to fetch streams from ksqlDB, using cached data: {}", e.getMessage());
        }

        return streamTableRepository.findByKsqlDbIdAndType(ksqlDBId, "STREAM").stream()
                .map(KsqlStreamTableEntity::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get all tables for a ksqlDB instance
     */
    @Transactional(readOnly = true)
    public List<KsqlStreamTable> getTables(String ksqlDBId) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Try to fetch from ksqlDB and sync
        try {
            List<KsqlDBService.KsqlTable> ksqlTables = ksqlDBService.getTables(ksqlDB);
            syncStreamsTables(ksqlDBId, ksqlTables, "TABLE");
        } catch (Exception e) {
            log.warn("Failed to fetch tables from ksqlDB, using cached data: {}", e.getMessage());
        }

        return streamTableRepository.findByKsqlDbIdAndType(ksqlDBId, "TABLE").stream()
                .map(KsqlStreamTableEntity::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Get a stream or table by name
     */
    @Transactional(readOnly = true)
    public KsqlStreamTable getStreamTable(String ksqlDBId, String name) {
        getKsqlDB(ksqlDBId); // Verify access

        return streamTableRepository.findByKsqlDbIdAndName(ksqlDBId, name)
                .map(KsqlStreamTableEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Stream/Table not found: " + name));
    }

    /**
     * Create a stream
     */
    @Transactional
    public KsqlStreamTable createStream(String ksqlDBId, String query) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Execute CREATE STREAM statement
        KsqlDBService.KsqlStatementResult result = ksqlDBService.executeStatement(ksqlDB, query, null);
        
        if (result.getErrorMessage() != null) {
            throw new RuntimeException("Failed to create stream: " + result.getErrorMessage());
        }

        // Extract stream name from query (basic parsing)
        String streamName = extractNameFromCreateStatement(query);
        
        // Fetch the created stream from ksqlDB
        List<KsqlDBService.KsqlStream> streams = ksqlDBService.getStreams(ksqlDB);
        KsqlDBService.KsqlStream createdStream = streams.stream()
                .filter(s -> s.getName().equalsIgnoreCase(streamName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Stream created but not found: " + streamName));

        // Save to database
        KsqlStreamTableEntity entity = KsqlStreamTableEntity.builder()
                .id(UUID.randomUUID().toString())
                .ksqlDbId(ksqlDBId)
                .name(createdStream.getName())
                .type("STREAM")
                .topicName(createdStream.getTopic())
                .keyFormat(createdStream.getKeyFormat())
                .valueFormat(createdStream.getValueFormat())
                .schema(createdStream.getSchema())
                .queryText(query)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .properties(new HashMap<>())
                .build();

        return streamTableRepository.save(entity).toDomain();
    }

    /**
     * Create a table
     */
    @Transactional
    public KsqlStreamTable createTable(String ksqlDBId, String query) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Execute CREATE TABLE statement
        KsqlDBService.KsqlStatementResult result = ksqlDBService.executeStatement(ksqlDB, query, null);
        
        if (result.getErrorMessage() != null) {
            throw new RuntimeException("Failed to create table: " + result.getErrorMessage());
        }

        // Extract table name from query (basic parsing)
        String tableName = extractNameFromCreateStatement(query);
        
        // Fetch the created table from ksqlDB
        List<KsqlDBService.KsqlTable> tables = ksqlDBService.getTables(ksqlDB);
        KsqlDBService.KsqlTable createdTable = tables.stream()
                .filter(t -> t.getName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Table created but not found: " + tableName));

        // Save to database
        KsqlStreamTableEntity entity = KsqlStreamTableEntity.builder()
                .id(UUID.randomUUID().toString())
                .ksqlDbId(ksqlDBId)
                .name(createdTable.getName())
                .type("TABLE")
                .topicName(createdTable.getTopic())
                .keyFormat(createdTable.getKeyFormat())
                .valueFormat(createdTable.getValueFormat())
                .schema(createdTable.getSchema())
                .queryText(query)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .properties(new HashMap<>())
                .build();

        return streamTableRepository.save(entity).toDomain();
    }

    /**
     * Drop a stream
     * SECURITY: Validates and sanitizes stream name to prevent injection attacks
     */
    @Transactional
    public boolean dropStream(String ksqlDBId, String streamName) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Validate and sanitize stream name to prevent SQL injection
        String sanitizedStreamName = validateAndSanitizeIdentifier(streamName);
        
        // Verify stream exists in database before dropping (additional security check)
        streamTableRepository.findByKsqlDbIdAndName(ksqlDBId, sanitizedStreamName)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + sanitizedStreamName));

        String dropStatement = "DROP STREAM IF EXISTS " + sanitizedStreamName + ";";
        KsqlDBService.KsqlStatementResult result = ksqlDBService.executeStatement(ksqlDB, dropStatement, null);
        
        if (result.getErrorMessage() != null) {
            throw new RuntimeException("Failed to drop stream: " + result.getErrorMessage());
        }

        // Delete from database
        streamTableRepository.deleteByKsqlDbIdAndName(ksqlDBId, sanitizedStreamName);
        return true;
    }

    /**
     * Drop a table
     * SECURITY: Validates and sanitizes table name to prevent injection attacks
     */
    @Transactional
    public boolean dropTable(String ksqlDBId, String tableName) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        // Validate and sanitize table name to prevent SQL injection
        String sanitizedTableName = validateAndSanitizeIdentifier(tableName);
        
        // Verify table exists in database before dropping (additional security check)
        streamTableRepository.findByKsqlDbIdAndName(ksqlDBId, sanitizedTableName)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + sanitizedTableName));

        String dropStatement = "DROP TABLE IF EXISTS " + sanitizedTableName + ";";
        KsqlDBService.KsqlStatementResult result = ksqlDBService.executeStatement(ksqlDB, dropStatement, null);
        
        if (result.getErrorMessage() != null) {
            throw new RuntimeException("Failed to drop table: " + result.getErrorMessage());
        }

        // Delete from database
        streamTableRepository.deleteByKsqlDbIdAndName(ksqlDBId, sanitizedTableName);
        return true;
    }

    /**
     * Sync streams/tables from ksqlDB to database
     */
    @Transactional
    public void syncStreamsTables(String ksqlDBId, List<?> ksqlItems, String type) {
        try {
            List<KsqlStreamTableEntity> existing = streamTableRepository.findByKsqlDbIdAndType(ksqlDBId, type);
            Map<String, KsqlStreamTableEntity> existingMap = existing.stream()
                    .collect(Collectors.toMap(e -> e.getName().toLowerCase(), e -> e));

            for (Object item : ksqlItems) {
                String name;
                String topic;
                String keyFormat;
                String valueFormat;
                String schema;

                if (item instanceof KsqlDBService.KsqlStream) {
                    KsqlDBService.KsqlStream stream = (KsqlDBService.KsqlStream) item;
                    name = stream.getName();
                    topic = stream.getTopic();
                    keyFormat = stream.getKeyFormat();
                    valueFormat = stream.getValueFormat();
                    schema = stream.getSchema();
                } else if (item instanceof KsqlDBService.KsqlTable) {
                    KsqlDBService.KsqlTable table = (KsqlDBService.KsqlTable) item;
                    name = table.getName();
                    topic = table.getTopic();
                    keyFormat = table.getKeyFormat();
                    valueFormat = table.getValueFormat();
                    schema = table.getSchema();
                } else {
                    continue;
                }

                KsqlStreamTableEntity entity = existingMap.get(name.toLowerCase());
                if (entity == null) {
                    // Create new
                    entity = KsqlStreamTableEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .ksqlDbId(ksqlDBId)
                            .name(name)
                            .type(type)
                            .topicName(topic)
                            .keyFormat(keyFormat)
                            .valueFormat(valueFormat)
                            .schema(schema)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .properties(new HashMap<>())
                            .build();
                } else {
                    // Update existing
                    entity.setTopicName(topic);
                    entity.setKeyFormat(keyFormat);
                    entity.setValueFormat(valueFormat);
                    entity.setSchema(schema);
                    entity.setUpdatedAt(LocalDateTime.now());
                }
                streamTableRepository.save(entity);
            }
        } catch (Exception e) {
            log.error("Failed to sync streams/tables: {}", e.getMessage(), e);
        }
    }

    private KsqlDB getKsqlDB(String id) {
        return ksqlDBRepository.findById(id)
                .map(KsqlDBEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("ksqlDB instance not found with id: " + id));
    }

    private String extractNameFromCreateStatement(String query) {
        // Basic parsing: CREATE STREAM/TABLE name ...
        String upper = query.toUpperCase().trim();
        int createIdx = upper.indexOf("CREATE");
        if (createIdx == -1) return null;
        
        int streamIdx = upper.indexOf("STREAM", createIdx);
        int tableIdx = upper.indexOf("TABLE", createIdx);
        
        int typeIdx = streamIdx != -1 ? streamIdx : tableIdx;
        if (typeIdx == -1) return null;
        
        int nameStart = typeIdx + (streamIdx != -1 ? "STREAM".length() : "TABLE".length());
        String remaining = query.substring(nameStart).trim();
        
        // Extract name (until space, parenthesis, or semicolon)
        StringBuilder name = new StringBuilder();
        for (char c : remaining.toCharArray()) {
            if (Character.isWhitespace(c) || c == '(' || c == ';') {
                break;
            }
            name.append(c);
        }
        
        return name.toString().trim();
    }

    /**
     * Validates and sanitizes KSQL identifier (stream/table name) to prevent injection attacks.
     * KSQL identifiers must:
     * - Start with a letter or underscore
     * - Contain only letters, digits, and underscores
     * - Not contain SQL keywords or special characters
     * 
     * @param identifier The identifier to validate
     * @return Sanitized identifier
     * @throws IllegalArgumentException if identifier is invalid
     */
    private String validateAndSanitizeIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        String trimmed = identifier.trim();
        
        // Check length (KSQL has reasonable limits)
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Identifier too long (max 255 characters)");
        }

        // KSQL identifiers must start with letter or underscore, and contain only alphanumeric and underscore
        if (!trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid identifier format. Must start with letter or underscore and contain only alphanumeric characters and underscores");
        }

        // Additional check: prevent common SQL injection patterns
        String upper = trimmed.toUpperCase();
        String[] dangerousKeywords = {"DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT", "UPDATE", "SELECT", "EXEC", "EXECUTE", "UNION", "SCRIPT"};
        for (String keyword : dangerousKeywords) {
            if (upper.contains(keyword)) {
                throw new IllegalArgumentException("Identifier contains potentially dangerous keyword: " + keyword);
            }
        }

        return trimmed;
    }
}

