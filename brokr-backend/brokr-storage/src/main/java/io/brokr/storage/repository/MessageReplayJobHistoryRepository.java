package io.brokr.storage.repository;

import io.brokr.storage.entity.MessageReplayJobHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageReplayJobHistoryRepository extends JpaRepository<MessageReplayJobHistoryEntity, String> {
    
    /**
     * Find all history records for a replay job - optimized query
     * Uses index: idx_replay_job_history_job_timestamp
     */
    @Query("SELECT h FROM MessageReplayJobHistoryEntity h " +
           "WHERE h.replayJobId = :replayJobId " +
           "ORDER BY h.timestamp DESC")
    Page<MessageReplayJobHistoryEntity> findByReplayJobId(
            @Param("replayJobId") String replayJobId,
            Pageable pageable
    );
    
    /**
     * Find recent history records - for monitoring
     * Uses index: idx_replay_job_history_timestamp
     */
    @Query("SELECT h FROM MessageReplayJobHistoryEntity h " +
           "WHERE h.timestamp >= :since " +
           "ORDER BY h.timestamp DESC")
    List<MessageReplayJobHistoryEntity> findRecentHistory(@Param("since") LocalDateTime since);
    
    /**
     * Delete old history records (for retention policy) - batch delete
     */
    @Modifying
    @Query("DELETE FROM MessageReplayJobHistoryEntity h WHERE h.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Delete all history records for a replay job - for job deletion
     */
    @Modifying
    @Query("DELETE FROM MessageReplayJobHistoryEntity h WHERE h.replayJobId = :replayJobId")
    void deleteByReplayJobId(@Param("replayJobId") String replayJobId);
}

