package io.brokr.storage.repository;

import io.brokr.storage.entity.KsqlStreamTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KsqlStreamTableRepository extends JpaRepository<KsqlStreamTableEntity, String> {
    List<KsqlStreamTableEntity> findByKsqlDbId(String ksqlDbId);

    List<KsqlStreamTableEntity> findByKsqlDbIdAndType(String ksqlDbId, String type);

    Optional<KsqlStreamTableEntity> findByKsqlDbIdAndName(String ksqlDbId, String name);

    boolean existsByKsqlDbIdAndName(String ksqlDbId, String name);

    void deleteByKsqlDbIdAndName(String ksqlDbId, String name);
}

