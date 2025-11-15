package io.brokr.kafka.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.brokr.core.model.MessageReplayJob;
import io.brokr.core.model.ReplayJobStatus;
import io.brokr.storage.entity.MessageReplayJobEntity;
import io.brokr.storage.repository.MessageReplayJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for scheduling and executing scheduled replay jobs.
 * Handles both one-time scheduled jobs and recurring jobs (cron-based).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplaySchedulerService {
    
    private final MessageReplayJobRepository replayJobRepository;
    private final MessageReplayService replayService;
    
    // Cron parser for Unix-style cron expressions (5 fields: minute hour day month weekday)
    private final CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );
    
    /**
     * Scheduled task that runs every minute to check for jobs due to execute.
     * Uses index: idx_replay_jobs_scheduled
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    @Transactional
    public void executeScheduledJobs() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<MessageReplayJobEntity> dueJobs = replayJobRepository.findScheduledJobsDue(now);
            
            if (dueJobs.isEmpty()) {
                return;
            }
            
            log.info("Found {} scheduled jobs due to execute", dueJobs.size());
            
            for (MessageReplayJobEntity entity : dueJobs) {
                try {
                    // Update last scheduled run time
                    entity.setLastScheduledRun(now);
                    
                    // If recurring, calculate next run time
                    if (Boolean.TRUE.equals(entity.getIsRecurring()) && entity.getScheduleCron() != null) {
                        LocalDateTime nextRun = calculateNextRunTime(entity.getScheduleCron(), entity.getScheduleTimezone(), now);
                        entity.setNextScheduledRun(nextRun);
                        log.debug("Recurring job {} - next run scheduled for {}", entity.getId(), nextRun);
                    } else {
                        // One-time scheduled job - clear next run
                        entity.setNextScheduledRun(null);
                    }
                    
                    // Reset status to PENDING if it was in a terminal state
                    if (entity.getStatus() == ReplayJobStatus.COMPLETED || entity.getStatus() == ReplayJobStatus.FAILED) {
                        entity.setStatus(ReplayJobStatus.PENDING);
                        entity.setRetryCount(0); // Reset retry count for recurring jobs
                    }
                    
                    replayJobRepository.save(entity);
                    
                    // Start execution
                    replayService.startReplayJobExecution(entity.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to execute scheduled job {}: {}", entity.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled job execution: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Scheduled task that runs every hour to update next run times for recurring jobs.
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void updateRecurringJobSchedules() {
        try {
            List<MessageReplayJobEntity> recurringJobs = replayJobRepository.findRecurringJobs();
            
            if (recurringJobs.isEmpty()) {
                return;
            }
            
            log.debug("Updating next run times for {} recurring jobs", recurringJobs.size());
            LocalDateTime now = LocalDateTime.now();
            
            for (MessageReplayJobEntity entity : recurringJobs) {
                try {
                    // Only update if next run time is not set or is in the past
                    if (entity.getNextScheduledRun() == null || entity.getNextScheduledRun().isBefore(now)) {
                        LocalDateTime nextRun = calculateNextRunTime(
                                entity.getScheduleCron(), 
                                entity.getScheduleTimezone(), 
                                now);
                        entity.setNextScheduledRun(nextRun);
                        entity.setStatus(ReplayJobStatus.PENDING);
                        replayJobRepository.save(entity);
                        
                        log.debug("Updated recurring job {} - next run: {}", entity.getId(), nextRun);
                    }
                } catch (Exception e) {
                    log.error("Failed to update schedule for recurring job {}: {}", entity.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error updating recurring job schedules: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Schedules a replay job for future execution.
     * 
     * @param job The replay job to schedule
     * @param scheduleTime The time to execute the job
     * @return The scheduled job
     */
    @Transactional
    public MessageReplayJob scheduleJob(MessageReplayJob job, LocalDateTime scheduleTime) {
        job.setNextScheduledRun(scheduleTime);
        job.setIsRecurring(false);
        job.setStatus(ReplayJobStatus.PENDING);
        
        MessageReplayJobEntity entity = MessageReplayJobEntity.fromDomain(job);
        if (job.getFilters() != null) {
            entity.setFiltersJson(replayService.serializeFilters(job.getFilters()));
        }
        if (job.getTransformation() != null) {
            entity.setTransformationJson(replayService.serializeTransformation(job.getTransformation()));
        }
        
        entity = replayJobRepository.save(entity);
        
        return entity.toDomain();
    }
    
    /**
     * Schedules a recurring replay job using a cron expression.
     * 
     * @param job The replay job to schedule
     * @param cronExpression Cron expression (e.g., "0 0 * * *" for daily at midnight)
     * @param timezone Timezone for the cron expression (default: UTC)
     * @return The scheduled job
     */
    @Transactional
    public MessageReplayJob scheduleRecurringJob(MessageReplayJob job, String cronExpression, String timezone) {
        String tz = timezone != null ? timezone : "UTC";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = calculateNextRunTime(cronExpression, tz, now);
        
        job.setScheduleCron(cronExpression);
        job.setScheduleTimezone(tz);
        job.setNextScheduledRun(nextRun);
        job.setIsRecurring(true);
        job.setStatus(ReplayJobStatus.PENDING);
        
        MessageReplayJobEntity entity = MessageReplayJobEntity.fromDomain(job);
        if (job.getFilters() != null) {
            entity.setFiltersJson(replayService.serializeFilters(job.getFilters()));
        }
        if (job.getTransformation() != null) {
            entity.setTransformationJson(replayService.serializeTransformation(job.getTransformation()));
        }
        
        entity = replayJobRepository.save(entity);
        log.debug("Scheduled recurring replay job {} with cron '{}' - next run: {}", 
                entity.getId(), cronExpression, nextRun);
        
        return entity.toDomain();
    }
    
    /**
     * Calculates the next run time based on a cron expression using cron-utils library.
     * Supports standard Unix cron expressions (5 fields: minute hour day month weekday).
     *
     * @param cronExpression The cron expression to parse (e.g., "0 0 * * *" for daily at midnight)
     * @param timezone The timezone for the cron expression
     * @param fromTime The time to calculate from
     * @return The next execution time
     */
    private LocalDateTime calculateNextRunTime(String cronExpression, String timezone, LocalDateTime fromTime) {
        try {
            ZoneId zoneId = ZoneId.of(timezone != null ? timezone : "UTC");
            ZonedDateTime zonedFrom = fromTime.atZone(zoneId);
            
            // Parse cron expression
            Cron cron = cronParser.parse(cronExpression);
            
            // Get execution time calculator
            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            
            // Calculate next execution time
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(zonedFrom);
            
            if (nextExecution.isPresent()) {
                ZonedDateTime nextZoned = nextExecution.get();
                log.debug("Calculated next run time for cron '{}': {} (from {})", 
                        cronExpression, nextZoned, zonedFrom);
                return nextZoned.toLocalDateTime();
            } else {
                log.warn("Could not calculate next execution time for cron '{}', using fallback", cronExpression);
                return fromTime.plusHours(1);
            }
        } catch (Exception e) {
            log.error("Failed to calculate next run time for cron '{}' in timezone '{}': {}", 
                    cronExpression, timezone, e.getMessage(), e);
            // Fallback: schedule for 1 hour from now
            return fromTime.plusHours(1);
        }
    }
}

