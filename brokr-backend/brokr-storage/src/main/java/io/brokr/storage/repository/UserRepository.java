package io.brokr.storage.repository;

import io.brokr.storage.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
    List<UserEntity> findByOrganizationId(String organizationId);
    Page<UserEntity> findByOrganizationId(String organizationId, Pageable pageable);
    List<UserEntity> findByOrganizationIdIn(List<String> organizationIds);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}