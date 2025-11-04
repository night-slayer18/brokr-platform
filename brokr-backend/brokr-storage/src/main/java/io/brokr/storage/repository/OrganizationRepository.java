package io.brokr.storage.repository;

import io.brokr.storage.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
    Optional<OrganizationEntity> findByName(String name);
    boolean existsByName(String name);
}