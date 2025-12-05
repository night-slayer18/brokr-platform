package io.brokr.api.service;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.exception.ValidationException;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.BrokerNode;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.kafka.service.JmxConnectionService;
import io.brokr.kafka.service.KafkaAdminService;
import io.brokr.kafka.service.KafkaConnectionService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.security.service.ClusterDataService;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterApiService {

    private final KafkaClusterRepository clusterRepository;
    private final KafkaConnectionService kafkaConnectionService;
    private final ClusterDataService clusterDataService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final JmxConnectionService jmxConnectionService;
    private final KafkaAdminService kafkaAdminService;

    @Transactional(readOnly = true)
    public List<KafkaCluster> listAuthorizedClusters(String organizationId, String environmentId) {
        return clusterDataService.getAuthorizedClusters(organizationId, environmentId);
    }

    @Transactional(readOnly = true)
    public KafkaCluster getClusterById(String id) {
        return clusterRepository.findById(id)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Map<String, List<KafkaCluster>> getClustersForOrganizations(List<String> organizationIds) {
        User currentUser = authorizationService.getCurrentUser();
        List<KafkaClusterEntity> allClusters = clusterRepository.findByOrganizationIdIn(organizationIds);
        
        // Filter clusters based on user's role and permissions
        List<KafkaCluster> filteredClusters = allClusters.stream()
                .map(KafkaClusterEntity::toDomain)
                .filter(cluster -> {
                    // SUPER_ADMIN can see all clusters
                    if (currentUser.getRole() == Role.SUPER_ADMIN) {
                        return true;
                    }
                    // For other roles, filter by accessible environments
                    return currentUser.getAccessibleEnvironmentIds().contains(cluster.getEnvironmentId());
                })
                .collect(Collectors.toList());
        
        return filteredClusters.stream()
                .collect(Collectors.groupingBy(KafkaCluster::getOrganizationId));
    }

    @Transactional
    public KafkaCluster createCluster(KafkaClusterInput input) {
        if (clusterRepository.existsByNameAndOrganizationId(input.getName(), input.getOrganizationId())) {
            throw new ValidationException("Cluster with this name already exists in the organization");
        }

        KafkaCluster cluster = KafkaCluster.builder()
                .id(UUID.randomUUID().toString())
                .name(input.getName())
                .bootstrapServers(input.getBootstrapServers())
                .properties(input.getProperties())
                .isActive(input.getIsActive() != null ? input.getIsActive() : true)
                .description(input.getDescription())
                .organizationId(input.getOrganizationId())
                .environmentId(input.getEnvironmentId())
                .securityProtocol(input.getSecurityProtocol())
                .saslMechanism(input.getSaslMechanism())
                .saslUsername(input.getSaslUsername())
                .saslPassword(input.getSaslPassword())
                .sslTruststoreLocation(input.getSslTruststoreLocation())
                .sslTruststorePassword(input.getSslTruststorePassword())
                .sslKeystoreLocation(input.getSslKeystoreLocation())
                .sslKeystorePassword(input.getSslKeystorePassword())
                .sslKeyPassword(input.getSslKeyPassword())
                // JMX configuration
                .jmxEnabled(input.getJmxEnabled() != null ? input.getJmxEnabled() : false)
                .jmxPort(input.getJmxPort())
                .jmxAuthentication(input.getJmxAuthentication() != null ? input.getJmxAuthentication() : false)
                .jmxUsername(input.getJmxUsername())
                .jmxPassword(input.getJmxPassword())
                .jmxSsl(input.getJmxSsl() != null ? input.getJmxSsl() : false)
                .build();

        boolean isReachable = kafkaConnectionService.testConnection(cluster);
        if (!isReachable) {
            throw new ValidationException("Failed to connect to the Kafka cluster. Please check the bootstrap servers and security settings.");
        }
        cluster.setReachable(isReachable);

        KafkaCluster savedCluster = clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
        
        // Log audit event
        try {
            auditService.logCreate(AuditResourceType.CLUSTER, savedCluster.getId(), savedCluster.getName(), savedCluster);
        } catch (Exception e) {
            log.warn("Failed to log audit event for cluster creation: {}", e.getMessage());
        }
        
        return savedCluster;
    }

    @Transactional
    public KafkaCluster updateCluster(String id, KafkaClusterInput input) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        if (!entity.getName().equals(input.getName()) &&
                clusterRepository.existsByNameAndOrganizationId(input.getName(), entity.getOrganizationId())) {
            throw new ValidationException("Cluster with this name already exists in the organization");
        }
        
        entity.setName(input.getName());
        entity.setBootstrapServers(input.getBootstrapServers());
        entity.setProperties(input.getProperties());
        entity.setActive(input.getIsActive() != null ? input.getIsActive() : entity.isActive()); // Keep existing value if not provided
        entity.setDescription(input.getDescription());
        entity.setSecurityProtocol(input.getSecurityProtocol());
        entity.setSaslMechanism(input.getSaslMechanism());
        entity.setSaslUsername(input.getSaslUsername());
        entity.setSaslPassword(input.getSaslPassword());
        entity.setSslTruststoreLocation(input.getSslTruststoreLocation());
        entity.setSslTruststorePassword(input.getSslTruststorePassword());
        entity.setSslKeystoreLocation(input.getSslKeystoreLocation());
        entity.setSslKeystorePassword(input.getSslKeystorePassword());
        entity.setSslKeyPassword(input.getSslKeyPassword());
        // JMX configuration
        entity.setJmxEnabled(input.getJmxEnabled() != null ? input.getJmxEnabled() : entity.isJmxEnabled());
        entity.setJmxPort(input.getJmxPort() != null ? input.getJmxPort() : entity.getJmxPort());
        entity.setJmxAuthentication(input.getJmxAuthentication() != null ? input.getJmxAuthentication() : entity.isJmxAuthentication());
        entity.setJmxUsername(input.getJmxUsername() != null ? input.getJmxUsername() : entity.getJmxUsername());
        entity.setJmxPassword(input.getJmxPassword() != null ? input.getJmxPassword() : entity.getJmxPassword());
        entity.setJmxSsl(input.getJmxSsl() != null ? input.getJmxSsl() : entity.isJmxSsl());

        KafkaCluster oldCluster = entity.toDomain();
        KafkaCluster cluster = entity.toDomain();
        boolean isReachable = kafkaConnectionService.testConnection(cluster);
        cluster.setReachable(isReachable);

        KafkaCluster updatedCluster = clusterRepository.save(KafkaClusterEntity.fromDomain(cluster)).toDomain();
        
        // Log audit event
        try {
            auditService.logUpdate(AuditResourceType.CLUSTER, updatedCluster.getId(), updatedCluster.getName(), oldCluster, updatedCluster);
        } catch (Exception e) {
            log.warn("Failed to log audit event for cluster update: {}", e.getMessage());
        }
        
        return updatedCluster;
    }

    @Transactional
    public boolean deleteCluster(String id) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));
        
        KafkaCluster cluster = entity.toDomain();
        clusterRepository.deleteById(id);
        
        // Log audit event
        try {
            auditService.logDelete(AuditResourceType.CLUSTER, cluster.getId(), cluster.getName(), cluster);
        } catch (Exception e) {
            log.warn("Failed to log audit event for cluster deletion: {}", e.getMessage());
        }
        
        return true;
    }

    @Transactional
    public boolean testClusterConnection(String id) {
        return testClusterConnection(id, true);
    }

    @Transactional
    public boolean testClusterConnection(String id, boolean logAudit) {
        KafkaClusterEntity entity = clusterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + id));

        KafkaCluster cluster = entity.toDomain();
        boolean reachable = kafkaConnectionService.testConnection(cluster);

        entity.setReachable(reachable);
        entity.setLastConnectionCheck(System.currentTimeMillis());
        entity.setLastConnectionError(reachable ? null : "Connection failed");
        clusterRepository.save(entity);
        
        // Log audit event only if requested (skip for scheduled health checks)
        if (logAudit) {
            try {
                auditService.logConnectionTest(AuditResourceType.CLUSTER, cluster.getId(), cluster.getName(), reachable, 
                        reachable ? null : "Connection failed");
            } catch (Exception e) {
                log.warn("Failed to log audit event for connection test: {}", e.getMessage());
            }
        }
        
        return reachable;
    }
    
    /**
     * Test JMX connection to all brokers in a cluster.
     * Returns true if at least one broker is reachable via JMX.
     */
    @Transactional(readOnly = true)
    public boolean testJmxConnection(String clusterId) {
        KafkaCluster cluster = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Cluster not found with id: " + clusterId));
        
        if (!cluster.isJmxEnabled() || cluster.getJmxPort() == null) {
            log.warn("JMX is not enabled for cluster: {}", cluster.getName());
            return false;
        }
        
        // Get brokers first
        List<BrokerNode> brokers = kafkaAdminService.getClusterNodes(cluster);
        if (brokers == null || brokers.isEmpty()) {
            log.warn("No brokers found for cluster: {}", cluster.getName());
            return false;
        }
        
        // Test JMX connection to each broker
        int successCount = 0;
        for (BrokerNode broker : brokers) {
            boolean connected = jmxConnectionService.testConnection(cluster, broker.getHost(), cluster.getJmxPort());
            if (connected) {
                successCount++;
                log.info("JMX connection successful to broker {} ({}) in cluster: {}", 
                        broker.getId(), broker.getHost(), cluster.getName());
            } else {
                log.warn("JMX connection failed to broker {} ({}) in cluster: {}", 
                        broker.getId(), broker.getHost(), cluster.getName());
            }
        }
        
        log.info("JMX test complete for cluster {}: {}/{} brokers reachable", 
                cluster.getName(), successCount, brokers.size());
        return successCount > 0;
    }
}