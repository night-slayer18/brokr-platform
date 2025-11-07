package io.brokr.storage.repository;

import io.brokr.storage.entity.SchemaRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemaRegistryRepository extends JpaRepository<SchemaRegistryEntity, String> {
    List<SchemaRegistryEntity> findByClusterId(String clusterId);
    List<SchemaRegistryEntity> findByClusterIdIn(List<String> clusterIds);
    Optional<SchemaRegistryEntity> findByNameAndClusterId(String name, String clusterId);
    boolean existsByNameAndClusterId(String name, String clusterId);
}