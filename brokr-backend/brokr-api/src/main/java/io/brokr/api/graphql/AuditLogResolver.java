package io.brokr.api.graphql;

import io.brokr.api.service.AuditLogApiService;
import io.brokr.core.dto.AuditLogFilter;
import io.brokr.core.dto.AuditLogPage;
import io.brokr.core.dto.AuditLogPagination;
import io.brokr.core.dto.AuditLogStatistics;
import io.brokr.core.model.AuditLog;
import io.brokr.core.model.AuditResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.ZoneId;

@Controller
@RequiredArgsConstructor
public class AuditLogResolver {

    private final AuditLogApiService auditLogApiService;

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogPage auditLogs(
            @Argument AuditLogFilter filter,
            @Argument AuditLogPagination pagination
    ) {
        AuditLogFilter serviceFilter = filter != null ? filter : new AuditLogFilter();
        AuditLogPagination servicePagination = pagination != null ? pagination : new AuditLogPagination();

        Slice<AuditLog> slice = auditLogApiService.getAuditLogs(serviceFilter, servicePagination);
        return AuditLogPage.builder()
            .content(slice.getContent())
            .currentPage(slice.getNumber())
            .pageSize(slice.getSize())
            .hasNext(slice.hasNext())
            .hasPrevious(slice.hasPrevious())
            .build();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLog auditLog(@Argument Long id) {
        return auditLogApiService.getAuditLogById(id);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogPage auditLogsByUser(
            @Argument String userId,
            @Argument AuditLogPagination pagination
    ) {
        AuditLogPagination servicePagination = pagination != null ? pagination : new AuditLogPagination();

        Slice<AuditLog> slice = auditLogApiService.getAuditLogsByUser(userId, servicePagination);
        return AuditLogPage.builder()
            .content(slice.getContent())
            .currentPage(slice.getNumber())
            .pageSize(slice.getSize())
            .hasNext(slice.hasNext())
            .hasPrevious(slice.hasPrevious())
            .build();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogPage auditLogsByResource(
            @Argument AuditResourceType resourceType,
            @Argument String resourceId,
            @Argument AuditLogPagination pagination
    ) {
        AuditLogPagination servicePagination = pagination != null ? pagination : new AuditLogPagination();

        Slice<AuditLog> slice = auditLogApiService.getAuditLogsByResource(resourceType, resourceId, servicePagination);
        return AuditLogPage.builder()
            .content(slice.getContent())
            .currentPage(slice.getNumber())
            .pageSize(slice.getSize())
            .hasNext(slice.hasNext())
            .hasPrevious(slice.hasPrevious())
            .build();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogStatistics auditLogStatistics(@Argument AuditLogFilter filter) {
        AuditLogFilter serviceFilter = filter != null ? filter : new AuditLogFilter();
        return auditLogApiService.getAuditLogStatistics(serviceFilter);
    }

    @SchemaMapping(typeName = "AuditLog", field = "timestamp")
    public Long getTimestamp(AuditLog auditLog) {
        if (auditLog.getTimestamp() == null) {
            return null;
        }
        return auditLog.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
