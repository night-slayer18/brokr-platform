package io.brokr.kafka.service;

import io.brokr.core.model.KsqlDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

@Service
@Slf4j
public class KsqlDBService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean testConnection(KsqlDB ksqlDB) {
        try {
            // ksqlDB health check endpoint
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(ksqlDB.getUrl() + "/healthcheck"))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            // Add authentication if needed
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

            // Add authentication if needed
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
}

