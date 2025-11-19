package io.brokr.security.service;

import io.brokr.core.exception.AccessDeniedException;
import io.brokr.core.model.KafkaCluster;
import io.brokr.core.model.User;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ClusterDataService {

    private static final int MAX_CLUSTERS_WITHOUT_FILTER = 1000; // Maximum clusters to return when no filter is applied

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

        // 1. Fetch the raw cluster data from the repository using database queries (not in-memory filtering)
        if (isSuperAdmin && organizationId == null) {
            // SUPER_ADMIN with no organizationId filter
            if (environmentId != null) {
                // Filter by environment only - use database query instead of findAll() + stream filter
                clusters = clusterRepository.findByEnvironmentId(environmentId);
            } else {
                // Get all clusters with a reasonable limit to prevent OOM
                // For production deployments with many clusters, pagination should be added at the API layer
                Pageable pageable = PageRequest.of(0, MAX_CLUSTERS_WITHOUT_FILTER);
                clusters = clusterRepository.findAll(pageable).getContent();
            }
        } else {
            // Regular users or SUPER_ADMIN with specific organizationId
            // Use database queries with WHERE clauses for efficient filtering
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