package io.brokr.storage.repository;

import io.brokr.core.model.ReplayJobStatus;
import io.brokr.storage.entity.MessageReplayJobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReplayJobRepository extends JpaRepository<MessageReplayJobEntity, String> {
    
    /**
     * Find all jobs for a cluster with status filter - optimized query
     * Uses index: idx_replay_jobs_cluster_status_created
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.clusterId = :clusterId " +
           "AND (:status IS NULL OR j.status = :status) " +
           "ORDER BY j.createdAt DESC")
    Page<MessageReplayJobEntity> findByClusterIdAndStatus(
            @Param("clusterId") String clusterId,
            @Param("status") ReplayJobStatus status,
            Pageable pageable
    );
    
    /**
     * Find all jobs created by a user - optimized query
     * Uses index: idx_replay_jobs_created_by_created
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.createdBy = :createdBy " +
           "ORDER BY j.createdAt DESC")
    Page<MessageReplayJobEntity> findByCreatedBy(
            @Param("createdBy") String createdBy,
            Pageable pageable
    );
    
    /**
     * Find all jobs for a source topic - optimized query
     * Uses index: idx_replay_jobs_source_topic_created
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.sourceTopic = :sourceTopic " +
           "ORDER BY j.createdAt DESC")
    Page<MessageReplayJobEntity> findBySourceTopic(
            @Param("sourceTopic") String sourceTopic,
            Pageable pageable
    );
    
    /**
     * Find all active jobs (PENDING or RUNNING) - optimized query
     * Uses index: idx_replay_jobs_status
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.status IN ('PENDING', 'RUNNING') " +
           "ORDER BY j.createdAt ASC")
    List<MessageReplayJobEntity> findActiveJobs();
    
    /**
     * Find all active jobs for a cluster
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.clusterId = :clusterId " +
           "AND j.status IN ('PENDING', 'RUNNING') " +
           "ORDER BY j.createdAt ASC")
    List<MessageReplayJobEntity> findActiveJobsByClusterId(@Param("clusterId") String clusterId);
    
    /**
     * Find jobs by status - for monitoring and cleanup
     */
    List<MessageReplayJobEntity> findByStatus(ReplayJobStatus status);
    
    /**
     * Find jobs that have been running for too long (stuck jobs)
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.status = 'RUNNING' " +
           "AND j.startedAt < :cutoffTime")
    List<MessageReplayJobEntity> findStuckJobs(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Update job status - optimized update
     */
    @Modifying
    @Query("UPDATE MessageReplayJobEntity j SET j.status = :status WHERE j.id = :id")
    void updateStatus(@Param("id") String id, @Param("status") ReplayJobStatus status);
    
    /**
     * Update job status and startedAt timestamp
     */
    @Modifying
    @Query("UPDATE MessageReplayJobEntity j SET j.status = :status, j.startedAt = :startedAt WHERE j.id = :id")
    void updateStatusAndStartedAt(@Param("id") String id, @Param("status") ReplayJobStatus status, @Param("startedAt") LocalDateTime startedAt);
    
    /**
     * Update job status and completedAt timestamp
     */
    @Modifying
    @Query("UPDATE MessageReplayJobEntity j SET j.status = :status, j.completedAt = :completedAt, j.errorMessage = :errorMessage WHERE j.id = :id")
    void updateStatusAndCompletedAt(@Param("id") String id, @Param("status") ReplayJobStatus status, @Param("completedAt") LocalDateTime completedAt, @Param("errorMessage") String errorMessage);
    
    /**
     * Count jobs by status for a cluster - for monitoring
     */
    @Query("SELECT COUNT(j) FROM MessageReplayJobEntity j WHERE j.clusterId = :clusterId AND j.status = :status")
    long countByClusterIdAndStatus(@Param("clusterId") String clusterId, @Param("status") ReplayJobStatus status);
    
    /**
     * Find scheduled jobs that are due to run
     * Uses index: idx_replay_jobs_scheduled
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.nextScheduledRun IS NOT NULL " +
           "AND j.nextScheduledRun <= :now " +
           "AND j.status = 'PENDING' " +
           "ORDER BY j.nextScheduledRun ASC")
    List<MessageReplayJobEntity> findScheduledJobsDue(@Param("now") LocalDateTime now);
    
    /**
     * Find recurring jobs that need next run time calculated
     */
    @Query("SELECT j FROM MessageReplayJobEntity j " +
           "WHERE j.isRecurring = true " +
           "AND j.status IN ('COMPLETED', 'FAILED') " +
           "AND j.scheduleCron IS NOT NULL")
    List<MessageReplayJobEntity> findRecurringJobs();
    
    /**
     * Get cluster ID for a job - optimized query to avoid fetching full entity
     * Used for authorization checks without N+1 issues
     */
    @Query("SELECT j.clusterId FROM MessageReplayJobEntity j WHERE j.id = :id")
    Optional<String> findClusterIdById(@Param("id") String id);
}

