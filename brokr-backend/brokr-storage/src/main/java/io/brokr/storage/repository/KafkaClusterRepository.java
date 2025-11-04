package io.brokr.storage.repository;

import io.brokr.storage.entity.KafkaClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KafkaClusterRepository extends JpaRepository<KafkaClusterEntity, String> {
    List<KafkaClusterEntity> findByOrganizationId(String organizationId);
    List<KafkaClusterEntity> findByOrganizationIdAndEnvironmentId(String organizationId, String environmentId);
    Optional<KafkaClusterEntity> findByNameAndOrganizationId(String name, String organizationId);
    boolean existsByNameAndOrganizationId(String name, String organizationId);
}