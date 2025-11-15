package io.brokr.kafka.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokr.core.model.KsqlDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class KsqlDBService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean testConnection(KsqlDB ksqlDB) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/healthcheck"))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to connect to ksqlDB: {}", ksqlDB.getUrl(), e);
            return false;
        }
    }

    public String getServerInfo(KsqlDB ksqlDB) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/info"))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get server info from ksqlDB: {}", ksqlDB.getUrl(), e);
            return null;
        }
    }

    /**
     * Execute a ksqlDB query (SELECT statements)
     */
    public KsqlQueryResult executeQuery(KsqlDB ksqlDB, String query, Map<String, Object> properties) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ksql", query);
            if (properties != null && !properties.isEmpty()) {
                requestBody.put("streamsProperties", properties);
            }

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/query"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Query execution failed: " + response.body());
            }

            return parseQueryResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to execute query on ksqlDB: {}", ksqlDB.getUrl(), e);
            throw new RuntimeException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a ksqlDB statement (DDL statements like CREATE, DROP, etc.)
     */
    public KsqlStatementResult executeStatement(KsqlDB ksqlDB, String statement, Map<String, Object> properties) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ksql", statement);
            if (properties != null && !properties.isEmpty()) {
                requestBody.put("streamsProperties", properties);
            }

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/ksql"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                JsonNode errorNode = objectMapper.readTree(response.body());
                String errorMessage = errorNode.has("message") ? errorNode.get("message").asText() : response.body();
                throw new RuntimeException("Statement execution failed: " + errorMessage);
            }

            return parseStatementResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to execute statement on ksqlDB: {}", ksqlDB.getUrl(), e);
            throw new RuntimeException("Failed to execute statement: " + e.getMessage(), e);
        }
    }

    /**
     * Get all streams from ksqlDB using SHOW STREAMS statement
     */
    public List<KsqlStream> getStreams(KsqlDB ksqlDB) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ksql", "SHOW STREAMS;");

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/ksql"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get streams: " + response.body());
            }

            return parseStreamsResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to get streams from ksqlDB: {}", ksqlDB.getUrl(), e);
            throw new RuntimeException("Failed to get streams: " + e.getMessage(), e);
        }
    }

    /**
     * Get all tables from ksqlDB using SHOW TABLES statement
     */
    public List<KsqlTable> getTables(KsqlDB ksqlDB) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ksql", "SHOW TABLES;");

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/ksql"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get tables: " + response.body());
            }

            return parseTablesResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to get tables from ksqlDB: {}", ksqlDB.getUrl(), e);
            throw new RuntimeException("Failed to get tables: " + e.getMessage(), e);
        }
    }

    /**
     * Terminate a running query
     */
    public boolean terminateQuery(KsqlDB ksqlDB, String queryId) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("queryId", queryId);

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/ksql/terminate"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to terminate query {} on ksqlDB: {}", queryId, ksqlDB.getUrl(), e);
            return false;
        }
    }

    /**
     * Get running queries
     */
    public List<KsqlQuery> getQueries(KsqlDB ksqlDB) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/ksql/queries"))
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            if (ksqlDB.getUsername() != null && ksqlDB.getPassword() != null) {
                String auth = ksqlDB.getUsername() + ":" + ksqlDB.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                requestBuilder.header("Authorization", "Basic " + encodedAuth);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to get queries: " + response.body());
            }

            return parseQueriesResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to get queries from ksqlDB: {}", ksqlDB.getUrl(), e);
            throw new RuntimeException("Failed to get queries: " + e.getMessage(), e);
        }
    }

    // Response parsing methods
    private KsqlQueryResult parseQueryResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            KsqlQueryResult result = new KsqlQueryResult();
            List<String> columns = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (item.has("header")) {
                        JsonNode header = item.get("header");
                        if (header.has("schema")) {
                            JsonNode schema = header.get("schema");
                            if (schema.isArray()) {
                                for (JsonNode field : schema) {
                                    if (field.has("name")) {
                                        columns.add(field.get("name").asText());
                                    }
                                }
                            }
                        }
                    } else if (item.has("row")) {
                        JsonNode row = item.get("row");
                        if (row.has("columns")) {
                            JsonNode columnsNode = row.get("columns");
                            List<String> rowData = new ArrayList<>();
                            if (columnsNode.isArray()) {
                                for (JsonNode col : columnsNode) {
                                    rowData.add(col.isNull() ? null : col.asText());
                                }
                            }
                            rows.add(rowData);
                        }
                    } else if (item.has("errorMessage")) {
                        result.setErrorMessage(item.get("errorMessage").asText());
                    }
                }
            }

            result.setColumns(columns);
            result.setRows(rows);
            return result;
        } catch (Exception e) {
            log.error("Failed to parse query response", e);
            throw new RuntimeException("Failed to parse query response: " + e.getMessage(), e);
        }
    }

    private KsqlStatementResult parseStatementResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            KsqlStatementResult result = new KsqlStatementResult();

            if (root.isArray() && root.size() > 0) {
                JsonNode firstItem = root.get(0);
                if (firstItem.has("commandId")) {
                    result.setCommandId(firstItem.get("commandId").asText());
                }
                if (firstItem.has("commandStatus")) {
                    JsonNode status = firstItem.get("commandStatus");
                    if (status.has("status")) {
                        result.setCommandStatus(status.get("status").asText());
                    }
                    if (status.has("message")) {
                        result.setMessage(status.get("message").asText());
                    }
                }
                if (firstItem.has("commandSequenceNumber")) {
                    result.setCommandSequenceNumber(firstItem.get("commandSequenceNumber").asLong());
                }
                if (firstItem.has("errorMessage")) {
                    result.setErrorMessage(firstItem.get("errorMessage").asText());
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to parse statement response", e);
            throw new RuntimeException("Failed to parse statement response: " + e.getMessage(), e);
        }
    }

    private List<KsqlStream> parseStreamsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<KsqlStream> streams = new ArrayList<>();

            // ksqlDB SHOW STREAMS response format: array of statement results
            // Each result has a "streams" array containing stream information
            if (root.isArray()) {
                for (JsonNode item : root) {
                    // Check for streams array in the response
                    if (item.has("streams") && item.get("streams").isArray()) {
                        JsonNode streamsArray = item.get("streams");
                        for (JsonNode stream : streamsArray) {
                            KsqlStream ksqlStream = new KsqlStream();
                            if (stream.has("name")) {
                                ksqlStream.setName(stream.get("name").asText());
                            }
                            if (stream.has("topic")) {
                                ksqlStream.setTopic(stream.get("topic").asText());
                            }
                            if (stream.has("keyFormat")) {
                                ksqlStream.setKeyFormat(stream.get("keyFormat").asText());
                            }
                            if (stream.has("valueFormat")) {
                                ksqlStream.setValueFormat(stream.get("valueFormat").asText());
                            }
                            if (stream.has("schema")) {
                                ksqlStream.setSchema(stream.get("schema").toString());
                            }
                            streams.add(ksqlStream);
                        }
                    }
                    // Alternative format: direct array of streams
                    else if (item.has("name")) {
                        KsqlStream ksqlStream = new KsqlStream();
                        if (item.has("name")) {
                            ksqlStream.setName(item.get("name").asText());
                        }
                        if (item.has("topic")) {
                            ksqlStream.setTopic(item.get("topic").asText());
                        }
                        if (item.has("keyFormat")) {
                            ksqlStream.setKeyFormat(item.get("keyFormat").asText());
                        }
                        if (item.has("valueFormat")) {
                            ksqlStream.setValueFormat(item.get("valueFormat").asText());
                        }
                        if (item.has("schema")) {
                            ksqlStream.setSchema(item.get("schema").toString());
                        }
                        streams.add(ksqlStream);
                    }
                }
            }

            return streams;
        } catch (Exception e) {
            log.error("Failed to parse streams response", e);
            return Collections.emptyList();
        }
    }

    private List<KsqlTable> parseTablesResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<KsqlTable> tables = new ArrayList<>();

            // ksqlDB SHOW TABLES response format: array of statement results
            // Each result has a "tables" array containing table information
            if (root.isArray()) {
                for (JsonNode item : root) {
                    // Check for tables array in the response
                    if (item.has("tables") && item.get("tables").isArray()) {
                        JsonNode tablesArray = item.get("tables");
                        for (JsonNode table : tablesArray) {
                            KsqlTable ksqlTable = new KsqlTable();
                            if (table.has("name")) {
                                ksqlTable.setName(table.get("name").asText());
                            }
                            if (table.has("topic")) {
                                ksqlTable.setTopic(table.get("topic").asText());
                            }
                            if (table.has("keyFormat")) {
                                ksqlTable.setKeyFormat(table.get("keyFormat").asText());
                            }
                            if (table.has("valueFormat")) {
                                ksqlTable.setValueFormat(table.get("valueFormat").asText());
                            }
                            if (table.has("schema")) {
                                ksqlTable.setSchema(table.get("schema").toString());
                            }
                            tables.add(ksqlTable);
                        }
                    }
                    // Alternative format: direct array of tables
                    else if (item.has("name")) {
                        KsqlTable ksqlTable = new KsqlTable();
                        if (item.has("name")) {
                            ksqlTable.setName(item.get("name").asText());
                        }
                        if (item.has("topic")) {
                            ksqlTable.setTopic(item.get("topic").asText());
                        }
                        if (item.has("keyFormat")) {
                            ksqlTable.setKeyFormat(item.get("keyFormat").asText());
                        }
                        if (item.has("valueFormat")) {
                            ksqlTable.setValueFormat(item.get("valueFormat").asText());
                        }
                        if (item.has("schema")) {
                            ksqlTable.setSchema(item.get("schema").toString());
                        }
                        tables.add(ksqlTable);
                    }
                }
            }

            return tables;
        } catch (Exception e) {
            log.error("Failed to parse tables response", e);
            return Collections.emptyList();
        }
    }

    private List<KsqlQuery> parseQueriesResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<KsqlQuery> queries = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode query : root) {
                    KsqlQuery ksqlQuery = new KsqlQuery();
                    if (query.has("queryId")) {
                        ksqlQuery.setQueryId(query.get("queryId").asText());
                    }
                    if (query.has("queryString")) {
                        ksqlQuery.setQueryString(query.get("queryString").asText());
                    }
                    if (query.has("status")) {
                        ksqlQuery.setStatus(query.get("status").asText());
                    }
                    queries.add(ksqlQuery);
                }
            }

            return queries;
        } catch (Exception e) {
            log.error("Failed to parse queries response", e);
            return Collections.emptyList();
        }
    }

    // Response model classes
    public static class KsqlQueryResult {
        private String queryId;
        private List<String> columns = new ArrayList<>();
        private List<List<String>> rows = new ArrayList<>();
        private Long executionTimeMs;
        private String errorMessage;

        // Getters and setters
        public String getQueryId() { return queryId; }
        public void setQueryId(String queryId) { this.queryId = queryId; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public List<List<String>> getRows() { return rows; }
        public void setRows(List<List<String>> rows) { this.rows = rows; }
        public Long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class KsqlStatementResult {
        private String commandId;
        private String commandStatus;
        private String message;
        private Long commandSequenceNumber;
        private String errorMessage;

        // Getters and setters
        public String getCommandId() { return commandId; }
        public void setCommandId(String commandId) { this.commandId = commandId; }
        public String getCommandStatus() { return commandStatus; }
        public void setCommandStatus(String commandStatus) { this.commandStatus = commandStatus; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getCommandSequenceNumber() { return commandSequenceNumber; }
        public void setCommandSequenceNumber(Long commandSequenceNumber) { this.commandSequenceNumber = commandSequenceNumber; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class KsqlStream {
        private String name;
        private String topic;
        private String keyFormat;
        private String valueFormat;
        private String schema;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getKeyFormat() { return keyFormat; }
        public void setKeyFormat(String keyFormat) { this.keyFormat = keyFormat; }
        public String getValueFormat() { return valueFormat; }
        public void setValueFormat(String valueFormat) { this.valueFormat = valueFormat; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
    }

    public static class KsqlTable {
        private String name;
        private String topic;
        private String keyFormat;
        private String valueFormat;
        private String schema;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getKeyFormat() { return keyFormat; }
        public void setKeyFormat(String keyFormat) { this.keyFormat = keyFormat; }
        public String getValueFormat() { return valueFormat; }
        public void setValueFormat(String valueFormat) { this.valueFormat = valueFormat; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
    }

    public static class KsqlQuery {
        private String queryId;
        private String queryString;
        private String status;

        // Getters and setters
        public String getQueryId() { return queryId; }
        public void setQueryId(String queryId) { this.queryId = queryId; }
        public String getQueryString() { return queryString; }
        public void setQueryString(String queryString) { this.queryString = queryString; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
