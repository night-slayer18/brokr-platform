package io.brokr.api.graphql;

import io.brokr.api.annotation.AuditLoggable;
import io.brokr.api.input.MessageReplayInput;
import io.brokr.api.service.MessageReplayApiService;
import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.MessageReplayJob;
import io.brokr.core.model.ReplayJobStatus;
import io.brokr.storage.entity.MessageReplayJobHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for message replay/reprocessing operations.
 */
@Controller
@RequiredArgsConstructor
public class ReplayResolver {

    private final MessageReplayApiService replayApiService;

    @QueryMapping
    @PreAuthorize("#clusterId == null or @authorizationService.hasAccessToCluster(#clusterId)")
    public List<MessageReplayJob> replayJobs(
            @Argument String clusterId,
            @Argument ReplayJobStatus status,
            @Argument Integer page,
            @Argument Integer size) {
        return replayApiService.listReplayJobs(
                clusterId,
                status,
                page != null ? page : 0,
                size != null ? size : 20
        );
    }

    @QueryMapping
    public MessageReplayJob replayJob(@Argument String id) {
        return replayApiService.getReplayJob(id);
    }

    @QueryMapping
    public List<MessageReplayJobHistoryEntity> replayHistory(
            @Argument String jobId,
            @Argument Integer page,
            @Argument Integer size) {
        return replayApiService.getReplayHistory(
                jobId,
                page != null ? page : 0,
                size != null ? size : 20
        );
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.MESSAGE_REPLAY, resourceNameParam = "input.sourceTopic", logResult = true)
    public MessageReplayJob replayMessages(@Argument MessageReplayInput input) {
        return replayApiService.replayMessages(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    @AuditLoggable(action = AuditActionType.CREATE, resourceType = AuditResourceType.MESSAGE_REPLAY, resourceNameParam = "input.sourceTopic", logResult = true)
    public MessageReplayJob scheduleReplay(@Argument MessageReplayInput input) {
        return replayApiService.scheduleReplay(input);
    }

    @MutationMapping
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.MESSAGE_REPLAY, resourceIdParam = "id")
    public boolean cancelReplay(@Argument String id) {
        return replayApiService.cancelReplay(id);
    }

    @MutationMapping
    @AuditLoggable(action = AuditActionType.UPDATE, resourceType = AuditResourceType.MESSAGE_REPLAY, resourceIdParam = "id")
    public boolean retryReplay(@Argument String id) {
        return replayApiService.retryReplay(id);
    }

    @MutationMapping
    @AuditLoggable(action = AuditActionType.DELETE, resourceType = AuditResourceType.MESSAGE_REPLAY, resourceIdParam = "id")
    public boolean deleteReplay(@Argument String id) {
        return replayApiService.deleteReplay(id);
    }
}

