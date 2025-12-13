package io.brokr.api.rest.controller;

import io.brokr.api.service.AuditLogApiService;
import io.brokr.core.dto.AuditLogFilter;
import io.brokr.core.dto.AuditLogPagination;
import io.brokr.core.dto.AuditLogStatistics;
import io.brokr.core.model.AuditLog;
import io.brokr.core.model.AuditResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for audit log operations.
 * Thin wrapper around AuditLogApiService - no service changes needed.
 */
@RestController
@RequestMapping("/api/v1/brokr/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    
    private final AuditLogApiService auditLogApiService;
    
    @GetMapping
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Slice<AuditLog> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String searchText,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        
        // Build filter
        AuditLogFilter filter = new AuditLogFilter();
        filter.setUserId(userId);
        if (actionType != null) {
            filter.setActionType(io.brokr.core.model.AuditActionType.valueOf(actionType));
        }
        if (resourceType != null) {
            filter.setResourceType(AuditResourceType.valueOf(resourceType));
        }
        filter.setResourceId(resourceId);
        filter.setOrganizationId(organizationId);
        filter.setClusterId(clusterId);
        if (status != null) {
            filter.setStatus(io.brokr.core.model.AuditStatus.valueOf(status));
        }
        if (severity != null) {
            filter.setSeverity(io.brokr.core.model.AuditSeverity.valueOf(severity));
        }
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        filter.setSearchText(searchText);
        
        // Build pagination
        AuditLogPagination pagination = new AuditLogPagination();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.setSortBy(sortBy);
        pagination.setSortDirection(sortDirection);
        
        return auditLogApiService.getAuditLogs(filter, pagination);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLog getAuditLog(@PathVariable Long id) {
        return auditLogApiService.getAuditLogById(id);
    }
    
    @GetMapping("/users/{userId}")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Slice<AuditLog> getAuditLogsByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        AuditLogPagination pagination = new AuditLogPagination();
        pagination.setPage(page);
        pagination.setSize(size);
        
        return auditLogApiService.getAuditLogsByUser(userId, pagination);
    }
    
    @GetMapping("/resources/{resourceType}/{resourceId}")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public Slice<AuditLog> getAuditLogsByResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        AuditLogPagination pagination = new AuditLogPagination();
        pagination.setPage(page);
        pagination.setSize(size);
        
        return auditLogApiService.getAuditLogsByResource(
                AuditResourceType.valueOf(resourceType), resourceId, pagination);
    }
    
    @GetMapping("/statistics")
    @PreAuthorize("@authorizationService.canManageUsers()")
    public AuditLogStatistics getAuditLogStatistics(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        AuditLogFilter filter = new AuditLogFilter();
        filter.setUserId(userId);
        if (actionType != null) {
            filter.setActionType(io.brokr.core.model.AuditActionType.valueOf(actionType));
        }
        if (resourceType != null) {
            filter.setResourceType(AuditResourceType.valueOf(resourceType));
        }
        filter.setOrganizationId(organizationId);
        filter.setClusterId(clusterId);
        filter.setStartTime(startTime);
        filter.setEndTime(endTime);
        
        return auditLogApiService.getAuditLogStatistics(filter);
    }
}

