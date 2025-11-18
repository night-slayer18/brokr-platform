package io.brokr.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokr.core.model.*;
import io.brokr.storage.entity.KafkaClusterEntity;
import io.brokr.storage.entity.MessageReplayJobEntity;
import io.brokr.storage.entity.MessageReplayJobHistoryEntity;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.MessageReplayJobHistoryRepository;
import io.brokr.storage.repository.MessageReplayJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Service for orchestrating message replay and reprocessing operations.
 * Optimized for enterprise-scale operations with no performance bottlenecks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageReplayService {
    
    // Configuration from application properties
    @Value("${message-replay.max-messages-per-job:10000000}")
    private long maxMessagesPerJob;
    
    @Value("${message-replay.max-concurrent-jobs:5}")
    private int maxConcurrentJobs;
    
    @Value("${message-replay.progress-update-interval:1000}")
    private int progressUpdateInterval;
    
    @Value("${message-replay.job-timeout-minutes:1440}")
    private int jobTimeoutMinutes;
    
    @Value("${message-replay.streaming-batch-size:10000}")
    private int streamingBatchSize;
    
    private final MessageReplayJobRepository replayJobRepository;
    private final MessageReplayJobHistoryRepository historyRepository;
    private final KafkaClusterRepository clusterRepository;
    private final KafkaConsumerService consumerService;
    private final KafkaProducerService producerService;
    private final KafkaAdminService adminService;
    private final ObjectMapper objectMapper;
    
    // Track running jobs for cancellation
    // CRITICAL: Use ConcurrentHashMap for thread safety in concurrent scenarios
    private final Map<String, CompletableFuture<Void>> runningJobs = new ConcurrentHashMap<>();
    
    // Track active partition replays to prevent duplicate replays
    // Key: "clusterId:sourceTopic:targetTopic:partitionId" or "clusterId:sourceTopic:consumerGroup:partitionId"
    // Value: jobId that is currently replaying this partition
    private final Map<String, String> activePartitionReplays = new ConcurrentHashMap<>();
    
    // Lock for atomic concurrent job limit check-and-add
    private final Object concurrentLimitLock = new Object();
    
    /**
     * Creates a new replay job and starts execution immediately.
     */
    @Transactional
    public MessageReplayJob createAndStartReplayJob(MessageReplayJob job) {
        // Validate job
        validateJob(job);
        
        // Check for partition conflicts before creating job
        checkPartitionConflicts(job);
        
        // Save job
        MessageReplayJobEntity entity = MessageReplayJobEntity.fromDomain(job);
        entity.setStatus(ReplayJobStatus.PENDING);
        
        // Serialize filters and transformation to JSON
        if (job.getFilters() != null) {
            entity.setFiltersJson(serializeFilters(job.getFilters()));
        }
        if (job.getTransformation() != null) {
            entity.setTransformationJson(serializeTransformation(job.getTransformation()));
        }
        
        entity = replayJobRepository.save(entity);
        
        // Start execution asynchronously
        startReplayJobExecution(entity.getId());
        
        return entity.toDomain();
    }
    
    /**
     * Replays messages to a consumer group (offset reset).
     */
    @Transactional
    public MessageReplayJob replayToConsumerGroup(
            String clusterId,
            String sourceTopic,
            String consumerGroupId,
            Long startOffset,
            LocalDateTime startTimestamp,
            List<Integer> partitions,
            String createdBy) {
        
        MessageReplayJob job = MessageReplayJob.builder()
                .id(UUID.randomUUID().toString())
                .clusterId(clusterId)
                .sourceTopic(sourceTopic)
                .consumerGroupId(consumerGroupId)
                .startOffset(startOffset)
                .startTimestamp(startTimestamp)
                .partitions(partitions)
                .status(ReplayJobStatus.PENDING)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
        
        return createAndStartReplayJob(job);
    }
    
    /**
     * Reprocesses messages to a different topic.
     */
    @Transactional
    public MessageReplayJob replayToTopic(
            String clusterId,
            String sourceTopic,
            String targetTopic,
            Long startOffset,
            LocalDateTime startTimestamp,
            Long endOffset,
            LocalDateTime endTimestamp,
            List<Integer> partitions,
            MessageFilter filter,
            MessageTransformation transformation,
            String createdBy) {
        
        MessageReplayJob job = MessageReplayJob.builder()
                .id(UUID.randomUUID().toString())
                .clusterId(clusterId)
                .sourceTopic(sourceTopic)
                .targetTopic(targetTopic)
                .startOffset(startOffset)
                .startTimestamp(startTimestamp)
                .endOffset(endOffset)
                .endTimestamp(endTimestamp)
                .partitions(partitions)
                .filters(filter)
                .transformation(transformation)
                .status(ReplayJobStatus.PENDING)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
        
        return createAndStartReplayJob(job);
    }
    
    /**
     * Starts replay job execution asynchronously.
     * Enforces concurrent job limits for enterprise-scale operations.
     * Uses dedicated thread pool for replay jobs.
     * 
     * RACE CONDITION FIX: This method should only be called from within 
     * a synchronized block that holds concurrentLimitLock.
     */
    @Async("replayJobExecutor")
    public void startReplayJobExecution(String jobId) {
        // Delegate to internal method with proper locking
        startReplayJobExecutionInternal(jobId);
    }
    
    /**
     * Internal method to start replay job execution with proper concurrency control.
     * CRITICAL: Must be called while holding concurrentLimitLock to prevent race conditions.
     * 
     * RACE CONDITION FIX:
     * - Checks available slots and registers job atomically
     * - Prevents multiple threads from exceeding maxConcurrentJobs
     * - Ensures job is added to runningJobs before lock is released
     */
    private void startReplayJobExecutionInternal(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        if (entity.getStatus() != ReplayJobStatus.PENDING) {
            log.warn("Job {} is not in PENDING status, current status: {}", jobId, entity.getStatus());
            return;
        }
        
        // Atomic check-and-add for concurrent job limit
        // CRITICAL: Use synchronized block to prevent race conditions
        synchronized (concurrentLimitLock) {
            // Re-check limit inside synchronized block (may have changed since call)
            if (runningJobs.size() >= maxConcurrentJobs) {
                log.warn("Maximum concurrent jobs ({}) reached. Job {} will remain in PENDING status.", 
                        maxConcurrentJobs, jobId);
                // Job remains PENDING, will be picked up when a slot becomes available
                return;
            }
            
            // Register partition locks before starting job
            registerPartitionLocks(entity);
            
            // Update status to RUNNING
            entity.setStatus(ReplayJobStatus.RUNNING);
            entity.setStartedAt(LocalDateTime.now());
            replayJobRepository.save(entity);
            
            // Record history
            recordHistory(jobId, "ACTION_STARTED", 0L, null, null);
            
            // Execute job with timeout
            CompletableFuture<Void> future = executeReplayJob(entity);
            
            // CRITICAL: Add to runningJobs BEFORE releasing lock
            // This ensures the count is updated atomically with the check
            runningJobs.put(jobId, future);
            
            log.info("Started replay job {} - Running jobs: {}/{}", 
                    jobId, runningJobs.size(), maxConcurrentJobs);
            
            // Add timeout handling (outside of critical section)
            CompletableFuture<Void> timeoutFuture = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(jobTimeoutMinutes * 60 * 1000L);
                    if (runningJobs.containsKey(jobId)) {
                        log.warn("Job {} exceeded timeout of {} minutes, cancelling", jobId, jobTimeoutMinutes);
                        future.cancel(true);
                        handleJobTimeout(jobId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            // Store entity reference for cleanup in whenComplete
            final MessageReplayJobEntity entityForCleanup = entity;
            future.whenComplete((result, throwable) -> {
                // Cleanup must also be atomic
                synchronized (concurrentLimitLock) {
                    // Clean up partition locks
                    unregisterPartitionLocks(entityForCleanup);
                    
                    // Remove from running jobs
                    runningJobs.remove(jobId);
                    
                    log.info("Completed replay job {} - Running jobs: {}/{}", 
                            jobId, runningJobs.size(), maxConcurrentJobs);
                }
                
                timeoutFuture.cancel(true);
                if (throwable != null && !(throwable instanceof java.util.concurrent.CancellationException)) {
                    handleJobFailure(jobId, throwable);
                }
                
                // Check if there are pending jobs that can now run
                // This will acquire its own lock
                processPendingJobs();
            });
        }
    }
    
    /**
     * Processes pending jobs when slots become available.
     * Called after a job completes to allow queued jobs to start.
     * 
     * RACE CONDITION FIX: Processes jobs outside of lock to avoid nested locking,
     * but each job start is atomic within startReplayJobExecutionInternal.
     */
    private void processPendingJobs() {
        // First, check if there are available slots (quick check without holding lock long)
        int availableSlots;
        synchronized (concurrentLimitLock) {
            availableSlots = maxConcurrentJobs - runningJobs.size();
        }
        
        if (availableSlots <= 0) {
            return;  // No slots available
        }
        
        // Find oldest pending jobs (limit to available slots)
        // This is done outside the lock to avoid holding it during DB query
        List<MessageReplayJobEntity> pendingJobs = replayJobRepository.findActiveJobs()
                .stream()
                .filter(job -> job.getStatus() == ReplayJobStatus.PENDING)
                .sorted(Comparator.comparing(MessageReplayJobEntity::getCreatedAt))
                .limit(availableSlots)
                .toList();
        
        if (pendingJobs.isEmpty()) {
            return;
        }
        
        log.debug("Found {} pending jobs to process, {} slots available", 
                pendingJobs.size(), availableSlots);
        
        // Start jobs one by one - each start is atomic
        // The internal method will re-check the limit atomically
        for (MessageReplayJobEntity job : pendingJobs) {
            try {
                // Call internal method directly (already async via @Async on public method)
                // The internal method will handle synchronization and limit checking
                startReplayJobExecutionInternal(job.getId());
            } catch (Exception e) {
                log.error("Failed to start pending job {}: {}", job.getId(), e.getMessage(), e);
                // Continue with next job
            }
        }
    }
    
    /**
     * Handles job timeout.
     */
    private void handleJobTimeout(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId).orElse(null);
        if (entity != null && entity.getStatus() == ReplayJobStatus.RUNNING) {
            entity.setStatus(ReplayJobStatus.FAILED);
            entity.setCompletedAt(LocalDateTime.now());
            entity.setErrorMessage("Job exceeded timeout of " + jobTimeoutMinutes + " minutes");
            replayJobRepository.save(entity);
            
            recordHistory(jobId, "ACTION_FAILED_TIMEOUT", 
                    entity.getProgressJson() != null ? extractMessageCount(entity.getProgressJson()) : 0L,
                    null, Map.of("timeoutMinutes", jobTimeoutMinutes));
            
            log.error("Job {} timed out after {} minutes", jobId, jobTimeoutMinutes);
        }
    }
    
    /**
     * Executes the replay job.
     */
    private CompletableFuture<Void> executeReplayJob(MessageReplayJobEntity entity) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Get cluster
                KafkaClusterEntity clusterEntity = clusterRepository.findById(entity.getClusterId())
                        .orElseThrow(() -> new RuntimeException("Cluster not found: " + entity.getClusterId()));
                KafkaCluster cluster = clusterEntity.toDomain();
                
                // Deserialize filters and transformation from JSON
                MessageFilter filter = deserializeFilters(entity.getFiltersJson());
                MessageTransformation transformation = deserializeTransformation(entity.getTransformationJson());
                
                // Progress tracking
                AtomicLong messagesProcessed = new AtomicLong(0);
                AtomicLong messagesMatched = new AtomicLong(0);
                long startTime = System.currentTimeMillis();
                
                // Progress callback
                Consumer<KafkaConsumerService.ReplayProgress> progressCallback = progress -> {
                    messagesProcessed.set(progress.getMessagesProcessed());
                    messagesMatched.set(progress.getMessagesMatched());
                    updateProgress(entity.getId(), messagesProcessed.get(), messagesMatched.get(), startTime);
                };
                
                // Process messages based on job type
                if (entity.getTargetTopic() != null) {
                    // Reprocess to topic - USE STREAMING to prevent OutOfMemoryError
                    // CRITICAL: Process in batches instead of loading all messages into memory
                    try {
                        final AtomicLong totalProduced = new AtomicLong(0);
                        
                        // Pass job ID to create unique consumer group and prevent conflicts
                        long totalMatched = consumerService.streamMessagesForReplay(
                                cluster,
                                entity.getSourceTopic(),
                                entity.getPartitions(),
                                entity.getStartOffset(),
                                entity.getStartTimestamp(),
                                entity.getEndOffset(),
                                entity.getEndTimestamp(),
                                filter,
                                maxMessagesPerJob,
                                streamingBatchSize,
                                batch -> {
                                    // Process batch: produce to target topic
                                    if (batch.isEmpty()) {
                                        return true; // Continue processing
                                    }
                                    
                                    long produced = producerService.produceBatch(
                                            cluster,
                                            entity.getTargetTopic(),
                                            batch,
                                            transformation
                                    );
                                    totalProduced.addAndGet(produced);
                                    
                                    // Update progress
                                    updateProgress(entity.getId(), messagesProcessed.get(), totalProduced.get(), startTime);
                                    
                                    return true; // Continue processing
                                },
                                progressCallback,
                                entity.getId() // Pass job ID for unique consumer group
                        );
                        
                        messagesMatched.set(totalMatched);
                        messagesProcessed.set(totalMatched); // For reporting
                        
                    } catch (Exception e) {
                        log.error("Failed to stream and produce messages to topic {} for job {}: {}", 
                                entity.getTargetTopic(), entity.getId(), e.getMessage(), e);
                        throw new RuntimeException("Failed to stream and produce messages: " + e.getMessage(), e);
                    }
                } else if (entity.getConsumerGroupId() != null) {
                    // Reset consumer group offset - no message consumption needed
                    try {
                        resetConsumerGroupOffset(cluster, entity.getSourceTopic(), entity.getConsumerGroupId(), 
                                entity.getStartOffset(), entity.getStartTimestamp(), entity.getPartitions());
                        messagesMatched.set(0); // No messages processed for offset reset
                        messagesProcessed.set(0);
                    } catch (Exception e) {
                        log.error("Failed to reset consumer group offset: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to reset consumer group offset: " + e.getMessage(), e);
                    }
                }
                
                // Mark as completed
                entity.setStatus(ReplayJobStatus.COMPLETED);
                entity.setCompletedAt(LocalDateTime.now());
                ReplayJobProgress progress = buildProgress(messagesProcessed.get(), messagesMatched.get(), startTime);
                entity.setProgressJson(serializeProgress(progress));
                replayJobRepository.save(entity);
                
                recordHistory(entity.getId(), "ACTION_COMPLETED", messagesMatched.get(), 
                        calculateThroughput(messagesMatched.get(), startTime), null);
                
                
            } catch (Exception e) {
                log.error("Replay job {} failed: {}", entity.getId(), e.getMessage(), e);
                handleJobFailure(entity.getId(), e);
            }
        });
    }
    
    /**
     * Resets consumer group offset.
     */
    private void resetConsumerGroupOffset(
            KafkaCluster cluster,
            String topic,
            String consumerGroupId,
            Long startOffset,
            LocalDateTime startTimestamp,
            List<Integer> partitions) {
        
        log.info("Resetting consumer group {} offset for topic {} (partitions: {})", 
                consumerGroupId, topic, partitions != null ? partitions : "all");
        
        adminService.resetConsumerGroupOffsets(
                cluster,
                consumerGroupId,
                topic,
                partitions,
                startOffset,
                startTimestamp
        );
        
        log.info("Successfully reset consumer group {} offset for topic {}", consumerGroupId, topic);
    }
    
    /**
     * Registers partition locks for a job to prevent duplicate replays.
     */
    private void registerPartitionLocks(MessageReplayJobEntity entity) {
        String clusterId = entity.getClusterId();
        String sourceTopic = entity.getSourceTopic();
        String targetTopic = entity.getTargetTopic();
        String consumerGroupId = entity.getConsumerGroupId();
        List<Integer> partitions = entity.getPartitions();
        String jobId = entity.getId();
        
        if (partitions != null && !partitions.isEmpty()) {
            // Register specific partitions
            for (Integer partition : partitions) {
                String key = buildPartitionKey(clusterId, sourceTopic, targetTopic, consumerGroupId, partition);
                activePartitionReplays.put(key, jobId);
            }
        } else {
            // Register all partitions (we'll need to get partition count from Kafka)
            // For now, we'll use a wildcard key
            String target = targetTopic != null ? targetTopic : consumerGroupId;
            if (target != null) {
                // Use a special key format for "all partitions"
                String key = String.format("%s:%s:%s:ALL", clusterId, sourceTopic, target);
                activePartitionReplays.put(key, jobId);
            }
        }
        
        log.debug("Registered partition locks for job {}: {} partitions", jobId, 
                partitions != null ? partitions.size() : "ALL");
    }
    
    /**
     * Unregisters partition locks for a job.
     */
    private void unregisterPartitionLocks(MessageReplayJobEntity entity) {
        String clusterId = entity.getClusterId();
        String sourceTopic = entity.getSourceTopic();
        String targetTopic = entity.getTargetTopic();
        String consumerGroupId = entity.getConsumerGroupId();
        List<Integer> partitions = entity.getPartitions();
        String jobId = entity.getId();
        
        if (partitions != null && !partitions.isEmpty()) {
            // Unregister specific partitions
            for (Integer partition : partitions) {
                String key = buildPartitionKey(clusterId, sourceTopic, targetTopic, consumerGroupId, partition);
                activePartitionReplays.remove(key, jobId); // Only remove if value matches
            }
        } else {
            // Unregister all partitions wildcard
            String target = targetTopic != null ? targetTopic : consumerGroupId;
            if (target != null) {
                String key = String.format("%s:%s:%s:ALL", clusterId, sourceTopic, target);
                activePartitionReplays.remove(key, jobId);
            }
        }
        
        log.debug("Unregistered partition locks for job {}", jobId);
    }
    
    /**
     * Cancels a running replay job.
     */
    @Transactional
    public void cancelReplayJob(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        if (entity.getStatus() != ReplayJobStatus.RUNNING) {
            throw new IllegalStateException("Job is not running, cannot cancel");
        }
        
        // Cancel the future
        CompletableFuture<Void> future = runningJobs.get(jobId);
        if (future != null) {
            future.cancel(true);
        }
        
        // Update status
        entity.setStatus(ReplayJobStatus.CANCELLED);
        entity.setCompletedAt(LocalDateTime.now());
        replayJobRepository.save(entity);
        
        recordHistory(jobId, "ACTION_CANCELLED", 
                entity.getProgressJson() != null ? extractMessageCount(entity.getProgressJson()) : 0L,
                null, null);
        
    }
    
    /**
     * Deletes a replay job.
     * Only allows deletion of non-running jobs (COMPLETED, FAILED, CANCELLED).
     * Automatically deletes associated history records.
     */
    @Transactional
    public void deleteReplayJob(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        // Prevent deletion of running or pending jobs
        if (entity.getStatus() == ReplayJobStatus.RUNNING || entity.getStatus() == ReplayJobStatus.PENDING) {
            throw new IllegalStateException("Cannot delete job with status: " + entity.getStatus() + ". Cancel the job first.");
        }
        
        // Remove from running jobs map if present (shouldn't be, but safety check)
        runningJobs.remove(jobId);
        
        // Clean up partition locks first
        unregisterPartitionLocks(entity);
        
        // Delete history records (cascade)
        historyRepository.deleteByReplayJobId(jobId);
        
        // Delete the job
        replayJobRepository.delete(entity);
    }
    
    /**
     * Gets replay job status and progress.
     */
    public MessageReplayJob getReplayJob(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        return entity.toDomain();
    }
    
    /**
     * Gets cluster ID for a job - optimized query to avoid fetching full entity.
     * Used for authorization checks without N+1 issues.
     */
    public Optional<String> getJobClusterId(String jobId) {
        return replayJobRepository.findClusterIdById(jobId);
    }
    
    /**
     * Lists replay jobs with filtering.
     */
    public List<MessageReplayJob> listReplayJobs(String clusterId, ReplayJobStatus status, int page, int size) {
        return replayJobRepository.findByClusterIdAndStatus(clusterId, status, 
                org.springframework.data.domain.PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(MessageReplayJobEntity::toDomain)
                .toList();
    }
    
    /**
     * Lists replay jobs created by a specific user.
     */
    public List<MessageReplayJob> listReplayJobsByUser(String createdBy, int page, int size) {
        return replayJobRepository.findByCreatedBy(createdBy, 
                org.springframework.data.domain.PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(MessageReplayJobEntity::toDomain)
                .toList();
    }
    
    /**
     * Lists replay jobs for a specific source topic.
     */
    public List<MessageReplayJob> listReplayJobsByTopic(String sourceTopic, int page, int size) {
        return replayJobRepository.findBySourceTopic(sourceTopic, 
                org.springframework.data.domain.PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(MessageReplayJobEntity::toDomain)
                .toList();
    }
    
    /**
     * Gets replay job history (detailed progress snapshots).
     */
    public List<MessageReplayJobHistoryEntity> listReplayHistory(String jobId, int page, int size) {
        return historyRepository.findByReplayJobId(jobId, 
                org.springframework.data.domain.PageRequest.of(page, size))
                .getContent();
    }
    
    /**
     * Gets recent replay history across all jobs (for monitoring).
     */
    public List<MessageReplayJobHistoryEntity> getRecentReplayHistory(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return historyRepository.findRecentHistory(since);
    }
    
    /**
     * Retries a failed replay job.
     */
    @Transactional
    public void retryReplayJob(String jobId) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        if (entity.getStatus() != ReplayJobStatus.FAILED) {
            throw new IllegalStateException("Job is not in FAILED status, cannot retry");
        }
        
        // Reset status and retry count
        entity.setStatus(ReplayJobStatus.PENDING);
        entity.setRetryCount(0);
        entity.setErrorMessage(null);
        entity.setCompletedAt(null);
        replayJobRepository.save(entity);
        
        startReplayJobExecution(jobId);
    }
    
    /**
     * Gets the number of currently running jobs.
     */
    public int getRunningJobCount() {
        return runningJobs.size();
    }
    
    /**
     * Gets the maximum concurrent jobs allowed.
     */
    public int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }
    
    /**
     * Checks for and handles stuck jobs (jobs that have been running too long).
     * This should be called periodically by a scheduled task.
     */
    @Transactional
    public void checkAndHandleStuckJobs() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(jobTimeoutMinutes);
        List<MessageReplayJobEntity> stuckJobs = replayJobRepository.findStuckJobs(cutoffTime);
        
        if (stuckJobs.isEmpty()) {
            return;
        }
        
        log.warn("Found {} stuck jobs (running for more than {} minutes)", stuckJobs.size(), jobTimeoutMinutes);
        
        for (MessageReplayJobEntity entity : stuckJobs) {
            try {
                // Cancel the future if it exists
                CompletableFuture<Void> future = runningJobs.get(entity.getId());
                if (future != null) {
                    future.cancel(true);
                    runningJobs.remove(entity.getId());
                }
                
                // Mark as failed due to timeout
                handleJobTimeout(entity.getId());
            } catch (Exception e) {
                log.error("Failed to handle stuck job {}: {}", entity.getId(), e.getMessage(), e);
            }
        }
    }
    
    // Helper methods
    
    /**
     * Checks for partition conflicts to prevent duplicate replays.
     * Throws exception if another job is already replaying the same partitions.
     */
    private void checkPartitionConflicts(MessageReplayJob job) {
        String clusterId = job.getClusterId();
        String sourceTopic = job.getSourceTopic();
        List<Integer> partitions = job.getPartitions();
        
        // If no partitions specified, we'll check all partitions (handled in startReplayJobExecution)
        // For now, we check if any of the specified partitions are already being replayed
        if (partitions != null && !partitions.isEmpty()) {
            for (Integer partition : partitions) {
                String targetKey = buildPartitionKey(clusterId, sourceTopic, job.getTargetTopic(), 
                        job.getConsumerGroupId(), partition);
                String existingJobId = activePartitionReplays.get(targetKey);
                if (existingJobId != null) {
                    throw new IllegalStateException(
                        String.format("Partition %d of topic %s is already being replayed by job %s. " +
                                "Please wait for that job to complete or cancel it first.",
                                partition, sourceTopic, existingJobId));
                }
            }
        } else {
            // All partitions - check if any job is replaying this topic to the same target
            String targetKeyPrefix = clusterId + ":" + sourceTopic + ":";
            String target = job.getTargetTopic() != null ? job.getTargetTopic() : job.getConsumerGroupId();
            if (target != null) {
                for (Map.Entry<String, String> entry : activePartitionReplays.entrySet()) {
                    if (entry.getKey().startsWith(targetKeyPrefix) && entry.getKey().contains(":" + target + ":")) {
                        throw new IllegalStateException(
                            String.format("Topic %s is already being replayed to %s by job %s. " +
                                    "Please wait for that job to complete or cancel it first.",
                                    sourceTopic, target, entry.getValue()));
                    }
                }
            }
        }
    }
    
    /**
     * Builds a unique key for tracking partition replays.
     */
    private String buildPartitionKey(String clusterId, String sourceTopic, String targetTopic, 
                                     String consumerGroupId, Integer partition) {
        String target = targetTopic != null ? targetTopic : consumerGroupId;
        return String.format("%s:%s:%s:%d", clusterId, sourceTopic, target, partition);
    }
    
    private void validateJob(MessageReplayJob job) {
        if (job.getClusterId() == null || job.getSourceTopic() == null) {
            throw new IllegalArgumentException("Cluster ID and source topic are required");
        }
        
        if (job.getTargetTopic() == null && job.getConsumerGroupId() == null) {
            throw new IllegalArgumentException("Either target topic or consumer group ID must be specified");
        }
        
        if (job.getStartOffset() != null && job.getStartTimestamp() != null) {
            throw new IllegalArgumentException("Cannot specify both start offset and start timestamp");
        }
    }
    
    /**
     * Updates progress for a replay job.
     * Optimized: Uses async write for history to avoid blocking, batches progress updates.
     * Thread-safe: Uses synchronized block to prevent race conditions in concurrent scenarios.
     */
    private synchronized void updateProgress(String jobId, long messagesProcessed, long messagesMatched, long startTime) {
        // Only update every progressUpdateInterval messages to reduce database load
        if (messagesProcessed % progressUpdateInterval == 0) {
            try {
                MessageReplayJobEntity entity = replayJobRepository.findById(jobId).orElse(null);
                if (entity != null) {
                    ReplayJobProgress progress = buildProgress(messagesProcessed, messagesMatched, startTime);
                    entity.setProgressJson(serializeProgress(progress));
                    replayJobRepository.save(entity);
                    
                    // Record history asynchronously to avoid blocking replay operation
                    // Use CompletableFuture to write history in background
                    CompletableFuture.runAsync(() -> {
                        try {
                            recordHistory(jobId, "MESSAGE_PROCESSED", messagesMatched, 
                                    calculateThroughput(messagesMatched, startTime), null);
                        } catch (Exception e) {
                            log.warn("Failed to record history for job {}: {}", jobId, e.getMessage());
                            // Don't throw - history is non-critical
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to update progress for job {}: {}", jobId, e.getMessage());
                // Don't throw - progress update failure shouldn't stop replay
            }
        }
    }
    
    private ReplayJobProgress buildProgress(long messagesProcessed, long messagesMatched, long startTime) {
        double throughput = calculateThroughput(messagesMatched, startTime);
        long estimatedTimeRemaining = messagesMatched > 0 && throughput > 0 ? 
                (long) ((messagesProcessed - messagesMatched) / throughput) : 0;
        
        return ReplayJobProgress.builder()
                .messagesProcessed(messagesProcessed)
                .messagesTotal(messagesMatched)  // Use messagesTotal field
                .throughput(throughput)
                .estimatedTimeRemainingSeconds(estimatedTimeRemaining)
                .build();
    }
    
    private double calculateThroughput(long messages, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > 0 ? (messages * 1000.0) / elapsed : 0.0;
    }
    
    private void handleJobFailure(String jobId, Throwable throwable) {
        MessageReplayJobEntity entity = replayJobRepository.findById(jobId).orElse(null);
        if (entity != null) {
            int currentRetryCount = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
            int maxRetries = entity.getMaxRetries() != null ? entity.getMaxRetries() : 0;
            
            // Check if we should retry
            if (maxRetries > 0 && currentRetryCount < maxRetries) {
                // Schedule retry
                int retryDelay = entity.getRetryDelaySeconds() != null ? entity.getRetryDelaySeconds() : 60;
                LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(retryDelay);
                
                entity.setStatus(ReplayJobStatus.PENDING);
                entity.setRetryCount(currentRetryCount + 1);
                entity.setNextScheduledRun(nextRetryTime);
                entity.setErrorMessage("Retry " + (currentRetryCount + 1) + "/" + maxRetries + ": " + throwable.getMessage());
                replayJobRepository.save(entity);
                
                log.info("Job {} failed, scheduling retry {}/{} at {}", 
                        jobId, currentRetryCount + 1, maxRetries, nextRetryTime);
                
                recordHistory(jobId, "ACTION_FAILED_RETRY", 
                        entity.getProgressJson() != null ? extractMessageCount(entity.getProgressJson()) : 0L,
                        null, Map.of("error", throwable.getMessage(), "retryCount", currentRetryCount + 1, "maxRetries", maxRetries));
                
                // Retry will be picked up by ReplaySchedulerService.executeScheduledJobs() which runs every 60 seconds
                // No need for manual scheduling - the scheduler checks nextScheduledRun
            } else {
                // No more retries, mark as failed
                entity.setStatus(ReplayJobStatus.FAILED);
                entity.setCompletedAt(LocalDateTime.now());
                entity.setErrorMessage(throwable.getMessage());
                replayJobRepository.save(entity);
                
                log.error("Job {} failed permanently after {} retries", jobId, currentRetryCount);
                
                recordHistory(jobId, "ACTION_FAILED", 
                        entity.getProgressJson() != null ? extractMessageCount(entity.getProgressJson()) : 0L,
                        null, Map.of("error", throwable.getMessage(), "finalRetryCount", currentRetryCount));
            }
        }
    }
    
    private void recordHistory(String jobId, String action, long messageCount, Double throughput, Map<String, Object> details) {
        MessageReplayJobHistoryEntity history = MessageReplayJobHistoryEntity.create(
                jobId, action, messageCount, throughput, details);
        historyRepository.save(history);
    }
    
    // JSON serialization/deserialization helpers
    private MessageFilter deserializeFilters(Map<String, Object> filtersJson) {
        if (filtersJson == null) return null;
        try {
            return objectMapper.convertValue(filtersJson, MessageFilter.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize filters: {}", e.getMessage());
            return null;
        }
    }
    
    private MessageTransformation deserializeTransformation(Map<String, Object> transformationJson) {
        if (transformationJson == null) return null;
        try {
            return objectMapper.convertValue(transformationJson, MessageTransformation.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize transformation: {}", e.getMessage());
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    Map<String, Object> serializeFilters(MessageFilter filter) {
        if (filter == null) return null;
        try {
            return objectMapper.convertValue(filter, Map.class);
        } catch (Exception e) {
            log.warn("Failed to serialize filters: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    Map<String, Object> serializeTransformation(MessageTransformation transformation) {
        if (transformation == null) return null;
        try {
            return objectMapper.convertValue(transformation, Map.class);
        } catch (Exception e) {
            log.warn("Failed to serialize transformation: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> serializeProgress(ReplayJobProgress progress) {
        try {
            return objectMapper.convertValue(progress, Map.class);
        } catch (Exception e) {
            log.warn("Failed to serialize progress: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    private long extractMessageCount(Map<String, Object> progressJson) {
        Object count = progressJson.get("messagesProcessed");
        return count instanceof Number ? ((Number) count).longValue() : 0L;
    }
}

