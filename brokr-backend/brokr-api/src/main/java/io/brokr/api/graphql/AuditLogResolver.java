package io.brokr.api.graphql;

import io.brokr.api.service.AuditLogApiService;
import io.brokr.core.model.AuditLog;
import io.brokr.core.model.AuditResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
        AuditLogApiService.AuditLogFilter serviceFilter = filter != null ? filter.toServiceFilter() : new AuditLogApiService.AuditLogFilter();
        AuditLogApiService.AuditLogPagination servicePagination = pagination != null ? pagination.toServicePagination() : new AuditLogApiService.AuditLogPagination();

        Page<AuditLog> page = auditLogApiService.getAuditLogs(serviceFilter, servicePagination);
        return new AuditLogPage(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
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
        AuditLogApiService.AuditLogPagination servicePagination = pagination != null ? pagination.toServicePagination() : new AuditLogApiService.AuditLogPagination();

        Page<AuditLog> page = auditLogApiService.getAuditLogsByUser(userId, servicePagination);
        return new AuditLogPage(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogPage auditLogsByResource(
            @Argument AuditResourceType resourceType,
            @Argument String resourceId,
            @Argument AuditLogPagination pagination
    ) {
        AuditLogApiService.AuditLogPagination servicePagination = pagination != null ? pagination.toServicePagination() : new AuditLogApiService.AuditLogPagination();

        Page<AuditLog> page = auditLogApiService.getAuditLogsByResource(resourceType, resourceId, servicePagination);
        return new AuditLogPage(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogStatistics auditLogStatistics(@Argument AuditLogFilter filter) {
        AuditLogApiService.AuditLogFilter serviceFilter = filter != null ? filter.toServiceFilter() : new AuditLogApiService.AuditLogFilter();
        return new AuditLogStatistics(auditLogApiService.getAuditLogStatistics(serviceFilter));
    }

    @SchemaMapping(typeName = "AuditLog", field = "timestamp")
    public Long getTimestamp(AuditLog auditLog) {
        if (auditLog.getTimestamp() == null) {
            return null;
        }
        return auditLog.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // GraphQL input/output types (will be auto-mapped by Spring GraphQL)
    public static class AuditLogFilter {
        // Delegate to service filter
        private AuditLogApiService.AuditLogFilter delegate = new AuditLogApiService.AuditLogFilter();
        
        public AuditLogApiService.AuditLogFilter toServiceFilter() {
            return delegate;
        }
        
        // Getters and setters that delegate
        public String getUserId() { return delegate.getUserId(); }
        public void setUserId(String userId) { delegate.setUserId(userId); }
        public io.brokr.core.model.AuditActionType getActionType() { return delegate.getActionType(); }
        public void setActionType(io.brokr.core.model.AuditActionType actionType) { delegate.setActionType(actionType); }
        public io.brokr.core.model.AuditResourceType getResourceType() { return delegate.getResourceType(); }
        public void setResourceType(io.brokr.core.model.AuditResourceType resourceType) { delegate.setResourceType(resourceType); }
        public String getResourceId() { return delegate.getResourceId(); }
        public void setResourceId(String resourceId) { delegate.setResourceId(resourceId); }
        public String getOrganizationId() { return delegate.getOrganizationId(); }
        public void setOrganizationId(String organizationId) { delegate.setOrganizationId(organizationId); }
        public String getClusterId() { return delegate.getClusterId(); }
        public void setClusterId(String clusterId) { delegate.setClusterId(clusterId); }
        public io.brokr.core.model.AuditStatus getStatus() { return delegate.getStatus(); }
        public void setStatus(io.brokr.core.model.AuditStatus status) { delegate.setStatus(status); }
        public io.brokr.core.model.AuditSeverity getSeverity() { return delegate.getSeverity(); }
        public void setSeverity(io.brokr.core.model.AuditSeverity severity) { delegate.setSeverity(severity); }
        public Long getStartTime() { return delegate.getStartTime(); }
        public void setStartTime(Long startTime) { delegate.setStartTime(startTime); }
        public Long getEndTime() { return delegate.getEndTime(); }
        public void setEndTime(Long endTime) { delegate.setEndTime(endTime); }
        public String getSearchText() { return delegate.getSearchText(); }
        public void setSearchText(String searchText) { delegate.setSearchText(searchText); }
    }
    
    public static class AuditLogPagination {
        private AuditLogApiService.AuditLogPagination delegate = new AuditLogApiService.AuditLogPagination();
        
        public AuditLogApiService.AuditLogPagination toServicePagination() {
            return delegate;
        }
        
        public int getPage() { return delegate.getPage(); }
        public void setPage(int page) { delegate.setPage(page); }
        public int getSize() { return delegate.getSize(); }
        public void setSize(int size) { delegate.setSize(size); }
        public String getSortBy() { return delegate.getSortBy(); }
        public void setSortBy(String sortBy) { delegate.setSortBy(sortBy); }
        public String getSortDirection() { return delegate.getSortDirection(); }
        public void setSortDirection(String sortDirection) { delegate.setSortDirection(sortDirection); }
    }
    
    public static class AuditLogPage {
        private final java.util.List<AuditLog> content;
        private final long totalElements;
        private final int totalPages;
        private final int currentPage;
        private final int pageSize;

        public AuditLogPage(java.util.List<AuditLog> content, long totalElements, int totalPages, int currentPage, int pageSize) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
        }

        public java.util.List<AuditLog> getContent() { return content; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
    }

    public static class AuditLogStatistics {
        private final AuditLogApiService.AuditLogStatistics delegate;
        
        public AuditLogStatistics(AuditLogApiService.AuditLogStatistics delegate) {
            this.delegate = delegate;
        }
        
        public long getTotalCount() { return delegate.getTotalCount(); }
        public java.util.List<AuditLogApiService.ActionTypeCount> getByActionType() { return delegate.getByActionType(); }
        public java.util.List<AuditLogApiService.ResourceTypeCount> getByResourceType() { return delegate.getByResourceType(); }
        public java.util.List<AuditLogApiService.StatusCount> getByStatus() { return delegate.getByStatus(); }
        public java.util.List<AuditLogApiService.SeverityCount> getBySeverity() { return delegate.getBySeverity(); }
        public java.util.List<AuditLogApiService.RecentActivity> getRecentActivity() { return delegate.getRecentActivity(); }
    }
}

