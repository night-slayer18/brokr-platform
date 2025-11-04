package io.brokr.kafka.service;

import io.brokr.core.model.KafkaConnect;
import io.brokr.core.model.Connector;
import io.brokr.core.model.ConnectorState;
import io.brokr.core.model.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class KafkaConnectService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean testConnection(KafkaConnect kafkaConnect) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(kafkaConnect.getUrl() + "/connectors"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
                String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(kafkaConnect.getUrl() + "/connectors"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to connect to Kafka Connect: {}", kafkaConnect.getUrl(), e);
            return false;
        }
    }

    public List<Connector> getConnectors(KafkaConnect kafkaConnect) {
        try {
            // Get list of connectors
            HttpRequest connectorsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(kafkaConnect.getUrl() + "/connectors"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
                String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                connectorsRequest = HttpRequest.newBuilder()
                        .uri(URI.create(kafkaConnect.getUrl() + "/connectors"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> connectorsResponse = httpClient.send(connectorsRequest, HttpResponse.BodyHandlers.ofString());
            if (connectorsResponse.statusCode() != 200) {
                return List.of();
            }

            JsonNode connectorsJson = objectMapper.readTree(connectorsResponse.body());
            List<Connector> connectors = new ArrayList<>();

            // Get details for each connector
            for (JsonNode connectorNode : connectorsJson) {
                String connectorName = connectorNode.asText();

                HttpRequest statusRequest = HttpRequest.newBuilder()
                        .uri(URI.create(kafkaConnect.getUrl() + "/connectors/" + connectorName + "/status"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                // Add authentication if needed
                if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
                    String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    statusRequest = HttpRequest.newBuilder()
                            .uri(URI.create(kafkaConnect.getUrl() + "/connectors/" + connectorName + "/status"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Accept", "application/json")
                            .header("Authorization", "Basic " + encodedAuth)
                            .GET()
                            .build();
                }

                HttpResponse<String> statusResponse = httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString());
                if (statusResponse.statusCode() == 200) {
                    JsonNode statusJson = objectMapper.readTree(statusResponse.body());

                    String connectorState = statusJson.get("connector").get("state").asText();
                    ConnectorState state = ConnectorState.valueOf(connectorState);

                    // Get tasks
                    List<Task> tasks = new ArrayList<>();
                    JsonNode tasksJson = statusJson.get("tasks");
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

                    // Get connector config
                    HttpRequest configRequest = HttpRequest.newBuilder()
                            .uri(URI.create(kafkaConnect.getUrl() + "/connectors/" + connectorName + "/config"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Accept", "application/json")
                            .GET()
                            .build();

                    // Add authentication if needed
                    if (kafkaConnect.getUsername() != null && kafkaConnect.getPassword() != null) {
                        String auth = kafkaConnect.getUsername() + ":" + kafkaConnect.getPassword();
                        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                        configRequest = HttpRequest.newBuilder()
                                .uri(URI.create(kafkaConnect.getUrl() + "/connectors/" + connectorName + "/config"))
                                .timeout(Duration.ofSeconds(5))
                                .header("Accept", "application/json")
                                .header("Authorization", "Basic " + encodedAuth)
                                .GET()
                                .build();
                    }

                    HttpResponse<String> configResponse = httpClient.send(configRequest, HttpResponse.BodyHandlers.ofString());
                    String config = "";
                    if (configResponse.statusCode() == 200) {
                        JsonNode configJson = objectMapper.readTree(configResponse.body());
                        config = configJson.toString();
                    }

                    connectors.add(Connector.builder()
                            .name(connectorName)
                            .type(configJson.has("connector.class") ? configJson.get("connector.class").asText() : "")
                            .state(state)
                            .config(config)
                            .tasks(tasks)
                            .build());
                }
            }

            return connectors;
        } catch (Exception e) {
            log.error("Failed to get connectors from Kafka Connect: {}", kafkaConnect.getUrl(), e);
            return List.of();
        }
    }
}