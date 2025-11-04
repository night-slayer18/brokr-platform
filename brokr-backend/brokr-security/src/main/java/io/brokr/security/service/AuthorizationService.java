package io.brokr.security.service;

import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.storage.entity.*;
import io.brokr.storage.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepository;
    private final KafkaClusterRepository clusterRepository;
    private final SchemaRegistryRepository schemaRegistryRepository;
    private final KafkaConnectRepository kafkaConnectRepository;
    private final KafkaStreamsApplicationRepository kafkaStreamsApplicationRepository;


    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .map(UserEntity::toDomain)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public boolean hasAccessToOrganization(String organizationId) {
        User user = getCurrentUser();

        // Super admins have access to all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Users can only access their own organization
        return user.getOrganizationId().equals(organizationId);
    }

    public boolean hasAccessToEnvironment(String environmentId) {
        User user = getCurrentUser();

        // Super admins have access to all environments
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Check if the environment is in the user's accessible environments
        return user.getAccessibleEnvironmentIds().contains(environmentId);
    }

    public boolean hasAccessToCluster(String clusterId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String environmentId = clusterRepository.findById(clusterId)
                .map(KafkaClusterEntity::getEnvironmentId)
                .orElseThrow(() -> new RuntimeException("Cluster not found for auth check"));

        return hasAccessToEnvironment(environmentId);
    }

    public boolean hasAccessToSchemaRegistry(String schemaRegistryId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = schemaRegistryRepository.findById(schemaRegistryId)
                .map(SchemaRegistryEntity::getClusterId)
                .orElseThrow(() -> new RuntimeException("Schema Registry not found for auth check"));

        return hasAccessToCluster(clusterId);
    }

    public boolean hasAccessToKafkaConnect(String kafkaConnectId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = kafkaConnectRepository.findById(kafkaConnectId)
                .map(KafkaConnectEntity::getClusterId)
                .orElseThrow(() -> new RuntimeException("Kafka Connect not found for auth check"));

        return hasAccessToCluster(clusterId);
    }

    public boolean hasAccessToKafkaStreamsApp(String kafkaStreamsApplicationId) {
        User user = getCurrentUser();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        String clusterId = kafkaStreamsApplicationRepository.findById(kafkaStreamsApplicationId)
                .map(KafkaStreamsApplicationEntity::getClusterId)
                .orElseThrow(() -> new RuntimeException("Kafka Streams Application not found for auth check"));

        return hasAccessToCluster(clusterId);
    }


    public boolean canManageTopics() {
        User user = getCurrentUser();
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canManageUsers() {
        User user = getCurrentUser();
        // <<< FIX: Added SUPER_ADMIN as it should also be able to manage users
        return user.getRole() == Role.SERVER_ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canManageOrganizations() {
        User user = getCurrentUser();
        return user.getRole() == Role.SUPER_ADMIN;
    }

    public boolean canAccessProdEnvironment() {
        User user = getCurrentUser();

        // Super admins can access prod
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // Admins can access prod
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        // Viewers cannot access prod
        return false;
    }
}