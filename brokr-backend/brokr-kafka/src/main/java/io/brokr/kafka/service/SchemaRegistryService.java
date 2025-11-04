package io.brokr.kafka.service;

import io.brokr.core.model.SchemaRegistry;
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
public class SchemaRegistryService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean testConnection(SchemaRegistry schemaRegistry) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistry.getUrl() + "/subjects"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.schemaregistry.v1+json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (schemaRegistry.getUsername() != null && schemaRegistry.getPassword() != null) {
                String auth = schemaRegistry.getUsername() + ":" + schemaRegistry.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(schemaRegistry.getUrl() + "/subjects"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/vnd.schemaregistry.v1+json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to connect to Schema Registry: {}", schemaRegistry.getUrl(), e);
            return false;
        }
    }

    public List<String> getSubjects(SchemaRegistry schemaRegistry) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistry.getUrl() + "/subjects"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.schemaregistry.v1+json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (schemaRegistry.getUsername() != null && schemaRegistry.getPassword() != null) {
                String auth = schemaRegistry.getUsername() + ":" + schemaRegistry.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(schemaRegistry.getUrl() + "/subjects"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/vnd.schemaregistry.v1+json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                List<String> subjects = new ArrayList<>();
                jsonNode.forEach(node -> subjects.add(node.asText()));
                return subjects;
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get subjects from Schema Registry: {}", schemaRegistry.getUrl(), e);
            return List.of();
        }
    }

    public String getLatestSchema(SchemaRegistry schemaRegistry, String subject) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistry.getUrl() + "/subjects/" + subject + "/versions/latest"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.schemaregistry.v1+json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (schemaRegistry.getUsername() != null && schemaRegistry.getPassword() != null) {
                String auth = schemaRegistry.getUsername() + ":" + schemaRegistry.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(schemaRegistry.getUrl() + "/subjects/" + subject + "/versions/latest"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/vnd.schemaregistry.v1+json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                return jsonNode.get("schema").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get schema from Schema Registry: {}", schemaRegistry.getUrl(), e);
            return null;
        }
    }

    public List<Integer> getSchemaVersions(SchemaRegistry schemaRegistry, String subject) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(schemaRegistry.getUrl() + "/subjects/" + subject + "/versions"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.schemaregistry.v1+json")
                    .GET()
                    .build();

            // Add authentication if needed
            if (schemaRegistry.getUsername() != null && schemaRegistry.getPassword() != null) {
                String auth = schemaRegistry.getUsername() + ":" + schemaRegistry.getPassword();
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                request = HttpRequest.newBuilder()
                        .uri(URI.create(schemaRegistry.getUrl() + "/subjects/" + subject + "/versions"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/vnd.schemaregistry.v1+json")
                        .header("Authorization", "Basic " + encodedAuth)
                        .GET()
                        .build();
            }

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode jsonNode = objectMapper.readTree(response.body());
                List<Integer> versions = new ArrayList<>();
                jsonNode.forEach(node -> versions.add(node.asInt()));
                return versions;
            }
            return List.of();
        } catch (Exception e) {
            log.error("Failed to get schema versions from Schema Registry: {}", schemaRegistry.getUrl(), e);
            return List.of();
        }
    }
}