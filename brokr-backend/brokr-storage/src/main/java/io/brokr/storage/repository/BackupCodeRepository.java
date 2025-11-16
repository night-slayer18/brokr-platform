package io.brokr.storage.repository;

import io.brokr.storage.entity.BackupCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupCodeRepository extends JpaRepository<BackupCodeEntity, String> {
    List<BackupCodeEntity> findByUserIdAndIsUsedFalse(String userId);
    List<BackupCodeEntity> findByUserId(String userId);
    
    @Query("SELECT b FROM BackupCodeEntity b WHERE b.userId = :userId AND b.isUsed = false")
    List<BackupCodeEntity> findUnusedByUserId(@Param("userId") String userId);
    
    long countByUserIdAndIsUsedFalse(String userId);
}

