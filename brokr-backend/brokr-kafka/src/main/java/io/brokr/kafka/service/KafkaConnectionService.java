package io.brokr.kafka.service;

import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.SecurityProtocol;
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

    public boolean testConnection(KafkaCluster cluster) {
        try (AdminClient adminClient = createAdminClient(cluster)) {
            // Try to list clusters with a short timeout
            adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            log.error("Connection timeout to cluster: {}", cluster.getName(), e);
            return false;
        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to connect to cluster: {}", cluster.getName(), e);
            return false;
        }
    }

    public AdminClient createAdminClient(KafkaCluster cluster) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
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
}