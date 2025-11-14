package io.brokr.security.service;

import io.brokr.core.exception.AccessDeniedException;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.User;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ClusterDataService {

    private final KafkaClusterRepository clusterRepository;
    private final AuthorizationService authorizationService;

    /**
     * Fetches a list of Kafka clusters that the current authenticated user
     * is authorized to see.
     */
    public List<KafkaCluster> getAuthorizedClusters(String organizationId, String environmentId) {
        User currentUser = authorizationService.getCurrentUser();

        boolean isSuperAdmin = currentUser.getRole() == io.brokr.core.model.Role.SUPER_ADMIN;
        
        if (organizationId == null && !isSuperAdmin) {
            organizationId = currentUser.getOrganizationId();
        }

        // Verify the user has access to this organization (skip for SUPER_ADMIN with null orgId)
        if (organizationId != null && !authorizationService.hasAccessToOrganization(organizationId)) {
            // This check is also in the resolver/controller, but it's good practice
            // to have it in the service layer as a hard-stop.
            throw new AccessDeniedException("Access denied to this organization");
        }

        List<KafkaClusterEntity> clusters;

        // 1. Fetch the raw cluster data from the repository
        if (isSuperAdmin && organizationId == null) {
            // SUPER_ADMIN with no organizationId filter - get all clusters
            if (environmentId != null) {
                // Filter by environment only
                clusters = clusterRepository.findAll().stream()
                        .filter(entity -> entity.getEnvironmentId().equals(environmentId))
                        .toList();
            } else {
                // Get all clusters
                clusters = clusterRepository.findAll();
            }
        } else {
            // Regular users or SUPER_ADMIN with specific organizationId
            if (environmentId != null) {
                clusters = clusterRepository.findByOrganizationIdAndEnvironmentId(organizationId, environmentId);
            } else {
                clusters = clusterRepository.findByOrganizationId(organizationId);
            }
        }

        // 2. Filter the results based on user's role and permissions
        Stream<KafkaClusterEntity> stream = clusters.stream();
        if (!isSuperAdmin) {
            stream = stream.filter(entity -> currentUser.getAccessibleEnvironmentIds().contains(entity.getEnvironmentId()));
        }

        // 3. Convert from Entity to Model and return
        return stream
                .map(KafkaClusterEntity::toDomain)
                .toList();
    }
}