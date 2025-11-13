package io.brokr.storage.repository;

import io.brokr.storage.entity.KsqlQueryMetricsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KsqlQueryMetricsRepository extends JpaRepository<KsqlQueryMetricsEntity, String> {
    Page<KsqlQueryMetricsEntity> findByQueryHistoryId(String queryHistoryId, Pageable pageable);

    @Query("SELECT m FROM KsqlQueryMetricsEntity m WHERE m.queryHistoryId = :queryHistoryId " +
           "AND m.timestamp BETWEEN :start AND :end ORDER BY m.timestamp ASC")
    List<KsqlQueryMetricsEntity> findByQueryHistoryIdAndTimestampBetween(
            @Param("queryHistoryId") String queryHistoryId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    void deleteByQueryHistoryId(String queryHistoryId);
}

