package io.brokr.security.service;

import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.security.model.BrokrUserDetails;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.KafkaConnectEntity;
import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import io.brokr.storage.entity.SchemaRegistryEntity;
import io.brokr.storage.repository.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
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
        Object principal = authentication.getPrincipal();

        // FIX: Get User model directly from the principal, no DB call
        if (principal instanceof BrokrUserDetails) {
            return ((BrokrUserDetails) principal).getUser();
        }

        // Fallback for any other principal type (should not happen in normal flow)
        // Note: authentication.getName() returns email since we use email for authentication
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(entity -> {
                    // Explicitly initialize lazy collection
                    Hibernate.initialize(entity.getAccessibleEnvironmentIds());
                    return entity.toDomain();
                })
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public boolean hasAccessToOrganization(String organizationId) {
        User user = getCurrentUser();

        // Super admins have access to all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }

        // FIX: Add null check to prevent NullPointerException
        if (user.getOrganizationId() == null) {
            return false;
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
        return user.getRole() == Role.SERVER_ADMIN 
            || user.getRole() == Role.SUPER_ADMIN
            || user.getRole() == Role.ADMIN;
    }

    /**
     * Check if user can manage users in a specific organization.
     * ADMIN can only manage users in their own organization.
     * SERVER_ADMIN and SUPER_ADMIN can manage users in any organization.
     */
    public boolean canManageUsersInOrganization(String organizationId) {
        User user = getCurrentUser();
        
        // SUPER_ADMIN and SERVER_ADMIN can manage all users
        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.SERVER_ADMIN) {
            return true;
        }
        
        // ADMIN can manage users in their own organization
        if (user.getRole() == Role.ADMIN) {
            return hasAccessToOrganization(organizationId);
        }
        
        return false;
    }

    /**
     * Check if user can manage their own organization (view and update, not create/delete).
     * ADMIN can manage their own organization.
     * SUPER_ADMIN can manage all organizations.
     */
    public boolean canManageOwnOrganization(String organizationId) {
        User user = getCurrentUser();
        
        // SUPER_ADMIN can manage all organizations
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        
        // ADMIN can manage their own organization
        if (user.getRole() == Role.ADMIN) {
            return hasAccessToOrganization(organizationId);
        }
        
        return false;
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