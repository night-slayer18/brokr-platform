package io.brokr.kafka.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SecurityProtocol;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class KafkaConnectionService {

    // Use Caffeine cache with size limit and TTL to prevent unbounded growth
    private final Cache<String, AdminClient> adminClientCache = Caffeine.newBuilder()
            .maximumSize(100) // Limit to 100 clusters
            .expireAfterAccess(30, TimeUnit.MINUTES) // Evict idle clients after 30 minutes
            .removalListener((key, value, cause) -> {
                if (value != null && value instanceof AdminClient) {
                    try {
                        ((AdminClient) value).close();
                        log.debug("Closed and evicted AdminClient for cluster: {}", key);
                    } catch (Exception e) {
                        log.warn("Error closing AdminClient for cluster {}: {}", key, e.getMessage());
                    }
                }
            })
            .build();

    public boolean testConnection(KafkaCluster cluster) {
        try {
            AdminClient adminClient = getOrCreateAdminClient(cluster);
            // Try to list clusters with a short timeout
            adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            log.error("Connection timeout to cluster: {}", cluster.getName(), e);
            // Remove broken client from cache
            removeAdminClient(cluster.getId());
            return false;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to connect to cluster: {}", cluster.getName(), e);
            // Remove broken client from cache
            removeAdminClient(cluster.getId());
            return false;
        }
    }

    /**
     * Gets or creates a cached AdminClient for the given cluster.
     * AdminClients are expensive to create, so we cache and reuse them.
     *
     * @param cluster The Kafka cluster configuration
     * @return A cached or newly created AdminClient
     */
    public AdminClient getOrCreateAdminClient(KafkaCluster cluster) {
        return adminClientCache.get(cluster.getId(), id -> {
            log.info("Creating new AdminClient for cluster: {}", cluster.getName());
            return createAdminClient(cluster);
        });
    }

    /**
     * Removes an AdminClient from the cache and closes it.
     * Useful when a client becomes broken or cluster configuration changes.
     *
     * @param clusterId The cluster ID
     */
    public void removeAdminClient(String clusterId) {
        AdminClient client = adminClientCache.getIfPresent(clusterId);
        if (client != null) {
            adminClientCache.invalidate(clusterId);
            try {
                client.close();
                log.info("Closed and removed AdminClient for cluster: {}", clusterId);
            } catch (Exception e) {
                log.warn("Error closing AdminClient for cluster {}: {}", clusterId, e.getMessage());
            }
        }
    }

    /**
     * Creates a new AdminClient instance. This method is now private
     * and should only be called by getOrCreateAdminClient.
     *
     * @param cluster The Kafka cluster configuration
     * @return A new AdminClient instance
     */
    private AdminClient createAdminClient(KafkaCluster cluster) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
        props.put(AdminClientConfig.METADATA_MAX_AGE_CONFIG, 60000);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        props.put(AdminClientConfig.RETRIES_CONFIG, 1);


        // Apply security configuration
        if (cluster.getSecurityProtocol() != null) {
            props.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, cluster.getSecurityProtocol().name());

            // Configure SASL if needed
            if (cluster.getSecurityProtocol() != SecurityProtocol.PLAINTEXT &&
                    cluster.getSecurityProtocol() != SecurityProtocol.SSL &&
                    cluster.getSaslMechanism() != null) {

                props.put(SaslConfigs.SASL_MECHANISM, cluster.getSaslMechanism());

                if (cluster.getSaslUsername() != null && cluster.getSaslPassword() != null) {
                    props.put(SaslConfigs.SASL_JAAS_CONFIG,
                            String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                                    cluster.getSaslUsername(), cluster.getSaslPassword()));
                }
            }

            // Configure SSL if needed
            if (cluster.getSecurityProtocol() == SecurityProtocol.SSL ||
                    cluster.getSecurityProtocol() == SecurityProtocol.SASL_SSL) {

                if (cluster.getSslTruststoreLocation() != null) {
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, cluster.getSslTruststoreLocation());
                }
                if (cluster.getSslTruststorePassword() != null) {
                    props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, cluster.getSslTruststorePassword());
                }
                if (cluster.getSslKeystoreLocation() != null) {
                    props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, cluster.getSslKeystoreLocation());
                }
                if (cluster.getSslKeystorePassword() != null) {
                    props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, cluster.getSslKeystorePassword());
                }
                if (cluster.getSslKeyPassword() != null) {
                    props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, cluster.getSslKeyPassword());
                }
            }
        }

        // Add any additional properties
        if (cluster.getProperties() != null) {
            props.putAll(cluster.getProperties());
        }

        return AdminClient.create(props);
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down KafkaConnectionService, closing {} AdminClient instances", adminClientCache.estimatedSize());
        adminClientCache.asMap().forEach((clusterId, client) -> {
            try {
                client.close();
                log.debug("Closed AdminClient for cluster: {}", clusterId);
            } catch (Exception e) {
                log.warn("Error closing AdminClient for cluster {}: {}", clusterId, e.getMessage());
            }
        });
        adminClientCache.invalidateAll();
    }
}