package io.brokr.storage.repository;

import io.brokr.storage.entity.KsqlDBEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KsqlDBRepository extends JpaRepository<KsqlDBEntity, String> {
    List<KsqlDBEntity> findByClusterId(String clusterId);
    List<KsqlDBEntity> findByClusterIdIn(List<String> clusterIds);
    Optional<KsqlDBEntity> findByNameAndClusterId(String name, String clusterId);
    boolean existsByNameAndClusterId(String name, String clusterId);
}

