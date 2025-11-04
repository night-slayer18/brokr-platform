package io.brokr.storage.repository;

import io.brokr.storage.entity.KafkaConnectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KafkaConnectRepository extends JpaRepository<KafkaConnectEntity, String> {
    List<KafkaConnectEntity> findByClusterId(String clusterId);
    Optional<KafkaConnectEntity> findByNameAndClusterId(String name, String clusterId);
    boolean existsByNameAndClusterId(String name, String clusterId);
}