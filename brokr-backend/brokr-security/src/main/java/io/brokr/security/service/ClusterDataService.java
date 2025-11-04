package io.brokr.security.service;

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

        // If organizationId is not provided, use the current user's organization
        if (organizationId == null) {
            organizationId = currentUser.getOrganizationId();
        }

        // Verify the user has access to this organization
        if (!authorizationService.hasAccessToOrganization(organizationId)) {
            // This check is also in the resolver/controller, but it's good practice
            // to have it in the service layer as a hard-stop.
            throw new RuntimeException("Access denied to this organization");
        }

        List<KafkaClusterEntity> clusters;

        // 1. Fetch the raw cluster data from the repository
        if (environmentId != null) {
            clusters = clusterRepository.findByOrganizationIdAndEnvironmentId(organizationId, environmentId);
        } else {
            clusters = clusterRepository.findByOrganizationId(organizationId);
        }

        // 2. Filter the results based on user's role and permissions
        Stream<KafkaClusterEntity> stream = clusters.stream();
        if (currentUser.getRole() != io.brokr.core.model.Role.SUPER_ADMIN) {
            stream = stream.filter(entity -> currentUser.getAccessibleEnvironmentIds().contains(entity.getEnvironmentId()));
        }

        // 3. Convert from Entity to Model and return
        return stream
                .map(KafkaClusterEntity::toDomain)
                .toList();
    }
}