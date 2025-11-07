package io.brokr.storage.repository;

import io.brokr.storage.entity.KafkaStreamsApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KafkaStreamsApplicationRepository extends JpaRepository<KafkaStreamsApplicationEntity, String> {
    List<KafkaStreamsApplicationEntity> findByClusterId(String clusterId);
    List<KafkaStreamsApplicationEntity> findByClusterIdIn(List<String> clusterIds);

    boolean existsByNameAndClusterId(String name, String clusterId);

    Optional<KafkaStreamsApplicationEntity> findByNameAndClusterId(String name, String clusterId);
}
