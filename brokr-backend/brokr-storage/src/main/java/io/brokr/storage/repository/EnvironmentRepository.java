package io.brokr.storage.repository;

import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, String> {
    List<EnvironmentEntity> findByOrganizationId(String organizationId);

    List<EnvironmentEntity> findByOrganizationIdIn(List<String> organizationIds);
    
    List<EnvironmentEntity> findByIdIn(List<String> ids);

    List<EnvironmentEntity> findByOrganizationIdAndType(String organizationId, EnvironmentType type);

    Optional<EnvironmentEntity> findByOrganizationIdAndName(String organizationId, String name);
}