package io.brokr.storage.repository;

import io.brokr.core.model.MfaType;
import io.brokr.storage.entity.MfaDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MfaDeviceRepository extends JpaRepository<MfaDeviceEntity, String> {
    Optional<MfaDeviceEntity> findByUserIdAndTypeAndIsActiveTrue(String userId, MfaType type);
    List<MfaDeviceEntity> findByUserIdAndIsActiveTrue(String userId);
    List<MfaDeviceEntity> findByUserId(String userId);
    boolean existsByUserIdAndTypeAndIsActiveTrue(String userId, MfaType type);
}

