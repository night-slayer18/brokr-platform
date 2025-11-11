package io.brokr.kafka.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokr.core.model.Connector;
import io.brokr.core.model.ConnectorState;
import io.brokr.core.model.KafkaConnect;
import io.brokr.core.model.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class KafkaConnectService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Tests connection to Kafka Connect asynchronously.
     * @return CompletableFuture that completes with true if connection is successful
     */
    public CompletableFuture<Boolean> testConnection(KafkaConnect kafkaConnect) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(kafkaConnect.getUrl() + "/connectors"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET();

        // Add authentication if needed
        if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
            String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            requestBuilder.header("Authorization", "Basic " + encodedAuth);
        }

        HttpRequest request = requestBuilder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(e -> {
                    log.error("Failed to connect to Kafka Connect: {}", kafkaConnect.getUrl(), e);
                    return false;
                });
    }

    /**
     * Gets connectors from Kafka Connect asynchronously.
     * @return CompletableFuture that completes with list of connectors
     */
    public CompletableFuture<List<Connector>> getConnectors(KafkaConnect kafkaConnect) {
        // Use expand parameter to get both status and info in one call
        // Format: expand=status,info (comma-separated) or make separate calls if needed
        String url = kafkaConnect.getUrl() + "/connectors?expand=status,info";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();

        // Add authentication if needed
        if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
            String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            requestBuilder.header("Authorization", "Basic " + encodedAuth);
        }

        HttpRequest connectorsRequest = requestBuilder.build();

        return httpClient.sendAsync(connectorsRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(connectorsResponse -> {
                    try {
                        if (connectorsResponse.statusCode() != 200) {
                            log.error("Failed to get connectors from {}. Status: {}", kafkaConnect.getUrl(), connectorsResponse.statusCode());
                            return List.<Connector>of();
                        }

                        JsonNode connectorsJson = objectMapper.readTree(connectorsResponse.body());
                        List<Connector> connectors = new ArrayList<>();

                        Iterator<Map.Entry<String, JsonNode>> fields = connectorsJson.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String connectorName = entry.getKey();
                            JsonNode details = entry.getValue();

                            JsonNode infoNode = details.get("info");
                            JsonNode statusNode = details.get("status");

                            if (infoNode == null || statusNode == null) {
                                log.warn("Connector {} missing info or status block in expanded response", connectorName);
                                continue;
                            }

                            // Get Config
                            JsonNode configNode = infoNode.get("config");
                            String config = configNode != null ? configNode.toString() : "{}";
                            String type = configNode != null && configNode.has("connector.class") ? configNode.get("connector.class").asText() : "unknown";

                            // Get State
                            JsonNode connectorStatus = statusNode.get("connector");
                            String stateString = connectorStatus != null && connectorStatus.has("state") ? connectorStatus.get("state").asText() : "UNASSIGNED";
                            ConnectorState state = ConnectorState.valueOf(stateString);

                            // Get Tasks
                            List<Task> tasks = new ArrayList<>();
                            JsonNode tasksJson = statusNode.get("tasks");
                            if (tasksJson != null && tasksJson.isArray()) {
                                for (JsonNode taskNode : tasksJson) {
                                    int taskId = taskNode.get("id").asInt();
                                    String taskState = taskNode.get("state").asText();
                                    String workerId = taskNode.get("worker_id").asText();
                                    String trace = taskNode.has("trace") ? taskNode.get("trace").asText() : null;

                                    tasks.add(Task.builder()
                                            .id(taskId)
                                            .state(taskState)
                                            .workerId(workerId)
                                            .trace(trace)
                                            .build());
                                }
                            }

                            connectors.add(Connector.builder()
                                    .name(connectorName)
                                    .type(type)
                                    .state(state)
                                    .config(config)
                                    .tasks(tasks)
                                    .build());
                        }

                        return connectors;
                    } catch (Exception e) {
                        log.error("Failed to get connectors from Kafka Connect: {}", kafkaConnect.getUrl(), e);
                        return List.<Connector>of();
                    }
                });
    }
}