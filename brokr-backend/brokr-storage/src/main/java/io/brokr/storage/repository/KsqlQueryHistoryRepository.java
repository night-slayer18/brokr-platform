package io.brokr.storage.repository;

import io.brokr.storage.entity.KsqlQueryHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KsqlQueryHistoryRepository extends JpaRepository<KsqlQueryHistoryEntity, String> {
    Page<KsqlQueryHistoryEntity> findByKsqlDbId(String ksqlDbId, Pageable pageable);

    Page<KsqlQueryHistoryEntity> findByUserId(String userId, Pageable pageable);

    Page<KsqlQueryHistoryEntity> findByKsqlDbIdAndStatus(String ksqlDbId, String status, Pageable pageable);

    @Query("SELECT h FROM KsqlQueryHistoryEntity h WHERE h.ksqlDbId = :ksqlDbId " +
           "AND h.startedAt BETWEEN :start AND :end ORDER BY h.startedAt DESC")
    Page<KsqlQueryHistoryEntity> findByKsqlDbIdAndStartedAtBetween(
            @Param("ksqlDbId") String ksqlDbId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("SELECT h FROM KsqlQueryHistoryEntity h WHERE h.ksqlDbId = :ksqlDbId " +
           "AND h.queryType = :queryType ORDER BY h.startedAt DESC")
    Page<KsqlQueryHistoryEntity> findByKsqlDbIdAndQueryType(
            @Param("ksqlDbId") String ksqlDbId,
            @Param("queryType") String queryType,
            Pageable pageable
    );

    @Query("SELECT h FROM KsqlQueryHistoryEntity h WHERE h.ksqlDbId = :ksqlDbId " +
           "AND h.status = :status AND h.queryType = :queryType ORDER BY h.startedAt DESC")
    Page<KsqlQueryHistoryEntity> findByKsqlDbIdAndStatusAndQueryType(
            @Param("ksqlDbId") String ksqlDbId,
            @Param("status") String status,
            @Param("queryType") String queryType,
            Pageable pageable
    );

    @Query("SELECT h FROM KsqlQueryHistoryEntity h WHERE h.ksqlDbId = :ksqlDbId " +
           "AND h.startedAt < :before")
    List<KsqlQueryHistoryEntity> findByKsqlDbIdAndStartedAtBefore(
            @Param("ksqlDbId") String ksqlDbId,
            @Param("before") LocalDateTime before
    );

    void deleteByKsqlDbIdAndStartedAtBefore(String ksqlDbId, LocalDateTime before);
}

