package io.brokr.api.service;

import io.brokr.api.input.MessageFilterInput;
import io.brokr.api.input.MessageReplayInput;
import io.brokr.api.input.MessageTransformationInput;
import io.brokr.core.model.*;
import io.brokr.kafka.service.MessageReplayService;
import io.brokr.kafka.service.ReplaySchedulerService;
import io.brokr.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API service layer for message replay operations.
 * Handles conversion between GraphQL input types and domain models.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageReplayApiService {

    private final MessageReplayService replayService;
    private final ReplaySchedulerService schedulerService;
    private final AuthorizationService authorizationService;

    /**
     * Creates and starts an immediate replay job.
     */
    @Transactional
    public MessageReplayJob replayMessages(MessageReplayInput input) {
        validateInput(input);
        String currentUserId = authorizationService.getCurrentUser().getId();
        
        MessageReplayJob job = convertInputToJob(input, currentUserId);
        return replayService.createAndStartReplayJob(job);
    }

    /**
     * Schedules a replay job for future execution.
     */
    @Transactional
    public MessageReplayJob scheduleReplay(MessageReplayInput input) {
        validateInput(input);
        String currentUserId = authorizationService.getCurrentUser().getId();
        
        MessageReplayJob job = convertInputToJob(input, currentUserId);
        
        if (input.getScheduleCron() != null) {
            // Recurring job
            return schedulerService.scheduleRecurringJob(
                    job,
                    input.getScheduleCron(),
                    input.getScheduleTimezone() != null ? input.getScheduleTimezone() : "UTC"
            );
        } else if (input.getScheduleTime() != null) {
            // One-time scheduled job
            return schedulerService.scheduleJob(job, input.getScheduleTime());
        } else {
            // Immediate execution
            return replayService.createAndStartReplayJob(job);
        }
    }

    /**
     * Cancels a running replay job.
     * Optimized to avoid N+1: uses direct clusterId lookup instead of fetching full job.
     */
    @Transactional
    public boolean cancelReplay(String jobId) {
        // Get cluster ID for authorization check - optimized query
        String clusterId = replayService.getJobClusterId(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        // Authorization check - user must have access to the cluster
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        replayService.cancelReplayJob(jobId);
        return true;
    }

    /**
     * Retries a failed replay job.
     * Optimized to avoid N+1: uses direct clusterId lookup instead of fetching full job.
     */
    @Transactional
    public boolean retryReplay(String jobId) {
        // Get cluster ID for authorization check - optimized query
        String clusterId = replayService.getJobClusterId(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        // Authorization check
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        replayService.retryReplayJob(jobId);
        return true;
    }

    /**
     * Deletes a replay job.
     * Only allows deletion of non-running jobs (COMPLETED, FAILED, CANCELLED).
     * Optimized to avoid N+1: uses direct clusterId lookup instead of fetching full job.
     */
    @Transactional
    public boolean deleteReplay(String jobId) {
        // Get cluster ID for authorization check - optimized query
        String clusterId = replayService.getJobClusterId(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        // Authorization check
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        replayService.deleteReplayJob(jobId);
        return true;
    }

    /**
     * Lists replay jobs with filtering.
     */
    @Transactional(readOnly = true)
    public List<MessageReplayJob> listReplayJobs(String clusterId, ReplayJobStatus status, int page, int size) {
        if (clusterId != null) {
            // Authorization check
            if (!authorizationService.hasAccessToCluster(clusterId)) {
                throw new SecurityException("Access denied to cluster: " + clusterId);
            }
            return replayService.listReplayJobs(clusterId, status, page, size);
        } else {
            // List jobs for current user
            String currentUserId = authorizationService.getCurrentUser().getId();
            return replayService.listReplayJobsByUser(currentUserId, page, size);
        }
    }

    /**
     * Gets a specific replay job.
     */
    @Transactional(readOnly = true)
    public MessageReplayJob getReplayJob(String id) {
        MessageReplayJob job = replayService.getReplayJob(id);
        // Authorization check
        if (!authorizationService.hasAccessToCluster(job.getClusterId())) {
            throw new SecurityException("Access denied to cluster: " + job.getClusterId());
        }
        return job;
    }

    /**
     * Gets replay job history.
     * Optimized to avoid N+1: uses direct clusterId lookup instead of fetching full job.
     */
    @Transactional(readOnly = true)
    public List<io.brokr.storage.entity.MessageReplayJobHistoryEntity> getReplayHistory(String jobId, int page, int size) {
        // Get cluster ID for authorization check - optimized query (avoids fetching full job)
        String clusterId = replayService.getJobClusterId(jobId)
                .orElseThrow(() -> new RuntimeException("Replay job not found: " + jobId));
        
        // Authorization check
        if (!authorizationService.hasAccessToCluster(clusterId)) {
            throw new SecurityException("Access denied to cluster: " + clusterId);
        }
        
        return replayService.listReplayHistory(jobId, page, size);
    }

    /**
     * Converts GraphQL input to domain model.
     */
    private MessageReplayJob convertInputToJob(MessageReplayInput input, String createdBy) {
        MessageReplayJob.MessageReplayJobBuilder builder = MessageReplayJob.builder()
                .clusterId(input.getClusterId())
                .sourceTopic(input.getSourceTopic())
                .targetTopic(input.getTargetTopic())
                .consumerGroupId(input.getConsumerGroupId())
                .startOffset(input.getStartOffset())
                .startTimestamp(input.getStartTimestamp())
                .endOffset(input.getEndOffset())
                .endTimestamp(input.getEndTimestamp())
                .partitions(input.getPartitions())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .status(ReplayJobStatus.PENDING)
                .maxRetries(input.getMaxRetries() != null ? input.getMaxRetries() : 0)
                .retryDelaySeconds(input.getRetryDelaySeconds() != null ? input.getRetryDelaySeconds() : 60);

        // Convert filters
        if (input.getFilters() != null) {
            builder.filters(convertFilterInput(input.getFilters()));
        }

        // Convert transformation
        if (input.getTransformation() != null) {
            builder.transformation(convertTransformationInput(input.getTransformation()));
        }

        return builder.build();
    }

    /**
     * Converts filter input to domain model.
     */
    private MessageFilter convertFilterInput(MessageFilterInput input) {
        MessageFilter.MessageFilterBuilder builder = MessageFilter.builder();

        if (input.getKeyFilter() != null) {
            builder.keyFilter(MessageFilter.KeyFilter.builder()
                    .type(convertKeyFilterType(input.getKeyFilter().getType()))
                    .value(input.getKeyFilter().getValue())
                    .build());
        }

        if (input.getValueFilter() != null) {
            builder.valueFilter(MessageFilter.ValueFilter.builder()
                    .type(convertValueFilterType(input.getValueFilter().getType()))
                    .value(input.getValueFilter().getValue())
                    .minSize(input.getValueFilter().getMinSize())
                    .maxSize(input.getValueFilter().getMaxSize())
                    .build());
        }

        if (input.getHeaderFilters() != null && !input.getHeaderFilters().isEmpty()) {
            List<MessageFilter.HeaderFilter> headerFilters = input.getHeaderFilters().stream()
                    .map(hf -> MessageFilter.HeaderFilter.builder()
                            .headerKey(hf.getHeaderKey())
                            .headerValue(hf.getHeaderValue())
                            .exactMatch(hf.getExactMatch() != null ? hf.getExactMatch() : true)
                            .build())
                    .collect(Collectors.toList());
            builder.headerFilters(headerFilters);
        }

        if (input.getTimestampRangeFilter() != null) {
            builder.timestampRangeFilter(MessageFilter.TimestampRangeFilter.builder()
                    .startTimestamp(input.getTimestampRangeFilter().getStartTimestamp())
                    .endTimestamp(input.getTimestampRangeFilter().getEndTimestamp())
                    .build());
        }

        if (input.getLogic() != null) {
            builder.logic(convertFilterLogic(input.getLogic()));
        }

        return builder.build();
    }

    /**
     * Converts transformation input to domain model.
     */
    private MessageTransformation convertTransformationInput(MessageTransformationInput input) {
        MessageTransformation.MessageTransformationBuilder builder = MessageTransformation.builder();

        if (input.getKeyTransformation() != null) {
            builder.keyTransformation(MessageTransformation.KeyTransformation.builder()
                    .type(convertKeyTransformationType(input.getKeyTransformation().getType()))
                    .newValue(input.getKeyTransformation().getNewValue())
                    .build());
        }

        if (input.getValueTransformation() != null) {
            builder.valueTransformation(MessageTransformation.ValueTransformation.builder()
                    .type(convertValueTransformationType(input.getValueTransformation().getType()))
                    .newValue(input.getValueTransformation().getNewValue())
                    .targetFormat(input.getValueTransformation().getTargetFormat())
                    .build());
        }

        builder.headerAdditions(input.getHeaderAdditions());
        builder.headerRemovals(input.getHeaderRemovals());

        return builder.build();
    }

    /**
     * Validates input before processing.
     */
    private void validateInput(MessageReplayInput input) {
        if (input.getClusterId() == null || input.getSourceTopic() == null) {
            throw new IllegalArgumentException("Cluster ID and source topic are required");
        }

        if (input.getTargetTopic() == null && input.getConsumerGroupId() == null) {
            throw new IllegalArgumentException("Either target topic or consumer group ID must be specified");
        }

        if (input.getTargetTopic() != null && input.getConsumerGroupId() != null) {
            throw new IllegalArgumentException("Cannot specify both target topic and consumer group ID");
        }

        if (input.getStartOffset() != null && input.getStartTimestamp() != null) {
            throw new IllegalArgumentException("Cannot specify both start offset and start timestamp");
        }

        if (input.getEndOffset() != null && input.getEndTimestamp() != null) {
            throw new IllegalArgumentException("Cannot specify both end offset and end timestamp");
        }

        // Authorization check
        if (!authorizationService.hasAccessToCluster(input.getClusterId())) {
            throw new SecurityException("Access denied to cluster: " + input.getClusterId());
        }
    }

    // Helper methods for enum conversion
    private MessageFilter.KeyFilterType convertKeyFilterType(MessageFilterInput.KeyFilterType type) {
        return MessageFilter.KeyFilterType.valueOf(type.name());
    }

    private MessageFilter.ValueFilterType convertValueFilterType(MessageFilterInput.ValueFilterType type) {
        return MessageFilter.ValueFilterType.valueOf(type.name());
    }

    private MessageFilter.FilterLogic convertFilterLogic(MessageFilterInput.FilterLogic logic) {
        return MessageFilter.FilterLogic.valueOf(logic.name());
    }

    private MessageTransformation.KeyTransformationType convertKeyTransformationType(MessageTransformationInput.KeyTransformationType type) {
        return MessageTransformation.KeyTransformationType.valueOf(type.name());
    }

    private MessageTransformation.ValueTransformationType convertValueTransformationType(MessageTransformationInput.ValueTransformationType type) {
        return MessageTransformation.ValueTransformationType.valueOf(type.name());
    }

}

