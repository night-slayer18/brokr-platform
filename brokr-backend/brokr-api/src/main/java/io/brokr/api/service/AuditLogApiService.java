package io.brokr.api.service;

import io.brokr.core.model.*;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.AuditLogEntity;
import io.brokr.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogApiService {

    private final AuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(AuditLogFilter filter, AuditLogPagination pagination) {
        // Only admins can view audit logs
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() != Role.SUPER_ADMIN && 
            currentUser.getRole() != Role.SERVER_ADMIN && 
            currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Unauthorized: Only admins can view audit logs");
        }

        // For ADMIN (org admins), enforce organization-level filtering
        // They can only see audit logs from their own organization
        String enforcedOrganizationId = null;
        if (currentUser.getRole() == Role.ADMIN) {
            if (currentUser.getOrganizationId() == null) {
                throw new RuntimeException("Unauthorized: Organization admin must belong to an organization");
            }
            enforcedOrganizationId = currentUser.getOrganizationId();
            // Override any organizationId filter - org admins can only see their org's logs
            filter.setOrganizationId(enforcedOrganizationId);
        }
        // SUPER_ADMIN and SERVER_ADMIN can see all audit logs (no filtering)

        // Build pagination
        Sort sort = Sort.by(
            pagination.getSortDirection().equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
            pagination.getSortBy()
        );
        Pageable pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), sort);

        // Convert filter
        // Use sentinel values for NULL timestamps to avoid PostgreSQL type inference issues
        LocalDateTime startTime = filter.getStartTime() != null ? 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(filter.getStartTime()), ZoneId.systemDefault()) : 
            LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime endTime = filter.getEndTime() != null ? 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(filter.getEndTime()), ZoneId.systemDefault()) : 
            LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        boolean hasStartTime = filter.getStartTime() != null;
        boolean hasEndTime = filter.getEndTime() != null;

        // Convert enum types to strings for native query
        String actionTypeStr = filter.getActionType() != null ? filter.getActionType().name() : null;
        String resourceTypeStr = filter.getResourceType() != null ? filter.getResourceType().name() : null;
        String statusStr = filter.getStatus() != null ? filter.getStatus().name() : null;
        String severityStr = filter.getSeverity() != null ? filter.getSeverity().name() : null;

        // Use repository method with filters
        Page<AuditLogEntity> entities = auditLogRepository.findWithFilters(
            filter.getUserId(),
            actionTypeStr,
            resourceTypeStr,
            filter.getResourceId(),
            filter.getOrganizationId(),
            filter.getClusterId(),
            statusStr,
            severityStr,
            startTime,
            endTime,
            hasStartTime,
            hasEndTime,
            filter.getSearchText(),
            pageable
        );

        // For ADMIN, also filter out audit logs from SUPER_ADMIN users (post-query filtering)
        if (currentUser.getRole() == Role.ADMIN) {
            List<AuditLogEntity> filteredEntities = entities.getContent().stream()
                .filter(entity -> {
                    // Exclude logs from SUPER_ADMIN and SERVER_ADMIN users
                    String userRole = entity.getUserRole();
                    return userRole == null || 
                           (!userRole.equals(Role.SUPER_ADMIN.name()) && 
                            !userRole.equals(Role.SERVER_ADMIN.name()));
                })
                .collect(Collectors.toList());
            
            // Recreate page with filtered content
            Pageable filteredPageable = PageRequest.of(pagination.getPage(), pagination.getSize(), sort);
            return new org.springframework.data.domain.PageImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                filteredPageable,
                filteredEntities.size()
            );
        }

        return entities.map(AuditLogEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public AuditLog getAuditLogById(Long id) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() != Role.SUPER_ADMIN && 
            currentUser.getRole() != Role.SERVER_ADMIN && 
            currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Unauthorized: Only admins can view audit logs");
        }

        AuditLogEntity entity = auditLogRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Audit log not found"));
        
        AuditLog auditLog = entity.toDomain();
        
        // For ADMIN (org admins), enforce organization-level access
        if (currentUser.getRole() == Role.ADMIN) {
            if (currentUser.getOrganizationId() == null) {
                throw new RuntimeException("Unauthorized: Organization admin must belong to an organization");
            }
            
            // Check if audit log belongs to their organization
            if (auditLog.getOrganizationId() == null || 
                !auditLog.getOrganizationId().equals(currentUser.getOrganizationId())) {
                throw new RuntimeException("Unauthorized: Cannot access audit log from another organization");
            }
            
            // Exclude audit logs from SUPER_ADMIN and SERVER_ADMIN users
            if (auditLog.getUserRole() != null && 
                (auditLog.getUserRole().equals(Role.SUPER_ADMIN.name()) || 
                 auditLog.getUserRole().equals(Role.SERVER_ADMIN.name()))) {
                throw new RuntimeException("Unauthorized: Cannot access audit log from super admin");
            }
        }
        // SUPER_ADMIN and SERVER_ADMIN can access any audit log
        
        return auditLog;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUser(String userId, AuditLogPagination pagination) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() != Role.SUPER_ADMIN && 
            currentUser.getRole() != Role.SERVER_ADMIN && 
            currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Unauthorized: Only admins can view audit logs");
        }

        // For ADMIN (org admins), they can only view logs for users in their organization
        final String enforcedOrganizationId;
        if (currentUser.getRole() == Role.ADMIN) {
            if (currentUser.getOrganizationId() == null) {
                throw new RuntimeException("Unauthorized: Organization admin must belong to an organization");
            }
            enforcedOrganizationId = currentUser.getOrganizationId();
        } else {
            enforcedOrganizationId = null;
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "timestamp");
        Pageable pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), sort);

        LocalDateTime startTime = LocalDateTime.now().minusDays(90); // Last 90 days
        LocalDateTime endTime = LocalDateTime.now();

        Page<AuditLogEntity> entities = auditLogRepository.findByUserIdAndTimestampBetween(
            userId, startTime, endTime, pageable
        );

        // For ADMIN, filter by organization and exclude SUPER_ADMIN/SERVER_ADMIN logs
        if (currentUser.getRole() == Role.ADMIN) {
            final String orgId = enforcedOrganizationId; // Make effectively final for lambda
            List<AuditLogEntity> filteredEntities = entities.getContent().stream()
                .filter(entity -> {
                    // Must belong to admin's organization
                    if (entity.getOrganizationId() == null || 
                        !entity.getOrganizationId().equals(orgId)) {
                        return false;
                    }
                    // Exclude logs from SUPER_ADMIN and SERVER_ADMIN users
                    String userRole = entity.getUserRole();
                    return userRole == null || 
                           (!userRole.equals(Role.SUPER_ADMIN.name()) && 
                            !userRole.equals(Role.SERVER_ADMIN.name()));
                })
                .collect(Collectors.toList());
            
            return new org.springframework.data.domain.PageImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                pageable,
                filteredEntities.size()
            );
        }

        return entities.map(AuditLogEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByResource(AuditResourceType resourceType, String resourceId, AuditLogPagination pagination) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() != Role.SUPER_ADMIN && 
            currentUser.getRole() != Role.SERVER_ADMIN && 
            currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Unauthorized: Only admins can view audit logs");
        }

        // For ADMIN (org admins), they can only view logs for resources in their organization
        final String enforcedOrganizationId;
        if (currentUser.getRole() == Role.ADMIN) {
            if (currentUser.getOrganizationId() == null) {
                throw new RuntimeException("Unauthorized: Organization admin must belong to an organization");
            }
            enforcedOrganizationId = currentUser.getOrganizationId();
        } else {
            enforcedOrganizationId = null;
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "timestamp");
        Pageable pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), sort);

        Page<AuditLogEntity> entities = auditLogRepository.findByResourceTypeAndResourceId(
            resourceType, resourceId, pageable
        );

        // For ADMIN, filter by organization and exclude SUPER_ADMIN/SERVER_ADMIN logs
        if (currentUser.getRole() == Role.ADMIN) {
            final String orgId = enforcedOrganizationId; // Make effectively final for lambda
            List<AuditLogEntity> filteredEntities = entities.getContent().stream()
                .filter(entity -> {
                    // Must belong to admin's organization
                    if (entity.getOrganizationId() == null || 
                        !entity.getOrganizationId().equals(orgId)) {
                        return false;
                    }
                    // Exclude logs from SUPER_ADMIN and SERVER_ADMIN users
                    String userRole = entity.getUserRole();
                    return userRole == null || 
                           (!userRole.equals(Role.SUPER_ADMIN.name()) && 
                            !userRole.equals(Role.SERVER_ADMIN.name()));
                })
                .collect(Collectors.toList());
            
            return new org.springframework.data.domain.PageImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                pageable,
                filteredEntities.size()
            );
        }

        return entities.map(AuditLogEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public AuditLogStatistics getAuditLogStatistics(AuditLogFilter filter) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser.getRole() != Role.SUPER_ADMIN && 
            currentUser.getRole() != Role.SERVER_ADMIN && 
            currentUser.getRole() != Role.ADMIN) {
            throw new RuntimeException("Unauthorized: Only admins can view audit log statistics");
        }

        // For ADMIN (org admins), enforce organization-level filtering
        String enforcedOrganizationId = null;
        if (currentUser.getRole() == Role.ADMIN) {
            if (currentUser.getOrganizationId() == null) {
                throw new RuntimeException("Unauthorized: Organization admin must belong to an organization");
            }
            enforcedOrganizationId = currentUser.getOrganizationId();
            // Override organizationId filter
            filter.setOrganizationId(enforcedOrganizationId);
        }

        LocalDateTime startTime = filter.getStartTime() != null ? 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(filter.getStartTime()), ZoneId.systemDefault()) : 
            LocalDateTime.now().minusDays(30);
        LocalDateTime endTime = filter.getEndTime() != null ? 
            LocalDateTime.ofInstant(Instant.ofEpochMilli(filter.getEndTime()), ZoneId.systemDefault()) : 
            LocalDateTime.now();

        // Get total count - for ADMIN, only count their org's logs
        long totalCount;
        if (currentUser.getRole() == Role.ADMIN) {
            totalCount = auditLogRepository.findByOrganizationIdAndTimestampBetween(
                enforcedOrganizationId, 
                LocalDateTime.of(1970, 1, 1, 0, 0), 
                LocalDateTime.of(9999, 12, 31, 23, 59, 59), 
                Pageable.unpaged()
            ).getTotalElements();
        } else {
            totalCount = auditLogRepository.count();
        }

        // Get counts by action type
        List<ActionTypeCount> byActionType = new ArrayList<>();
        for (AuditActionType actionType : AuditActionType.values()) {
            long count = auditLogRepository.countByActionTypeAndTimestampBetween(actionType, startTime, endTime);
            if (count > 0) {
                byActionType.add(new ActionTypeCount(actionType, count));
            }
        }

        // Get counts by resource type
        List<ResourceTypeCount> byResourceType = new ArrayList<>();
        for (AuditResourceType resourceType : AuditResourceType.values()) {
            long count = auditLogRepository.countByResourceTypeAndTimestampBetween(resourceType, startTime, endTime);
            if (count > 0) {
                byResourceType.add(new ResourceTypeCount(resourceType, count));
            }
        }

        // Get counts by status
        List<StatusCount> byStatus = new ArrayList<>();
        for (AuditStatus status : AuditStatus.values()) {
            long count = auditLogRepository.countByStatusAndTimestampBetween(status, startTime, endTime);
            if (count > 0) {
                byStatus.add(new StatusCount(status, count));
            }
        }

        // Get counts by severity
        List<SeverityCount> bySeverity = new ArrayList<>();
        for (AuditSeverity severity : AuditSeverity.values()) {
            long count = auditLogRepository.countBySeverityAndTimestampBetween(severity, startTime, endTime);
            if (count > 0) {
                bySeverity.add(new SeverityCount(severity, count));
            }
        }

        // Get recent activity (last 10)
        Pageable recentPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLogEntity> recentEntities;
        if (currentUser.getRole() == Role.ADMIN) {
            // For ADMIN, get logs from their organization only
            recentEntities = auditLogRepository.findByOrganizationIdAndTimestampBetween(
                enforcedOrganizationId, startTime, endTime, recentPageable
            );
        } else {
            recentEntities = auditLogRepository.findByTimestampBetween(startTime, endTime, recentPageable);
        }
        
        List<RecentActivity> recentActivity = recentEntities.getContent().stream()
            .filter(entity -> {
                // For ADMIN, exclude SUPER_ADMIN and SERVER_ADMIN logs
                if (currentUser.getRole() == Role.ADMIN) {
                    String userRole = entity.getUserRole();
                    return userRole == null || 
                           (!userRole.equals(Role.SUPER_ADMIN.name()) && 
                            !userRole.equals(Role.SERVER_ADMIN.name()));
                }
                return true;
            })
            .map(entity -> {
                AuditLog log = entity.toDomain();
                return new RecentActivity(
                    log.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    log.getActionType(),
                    log.getResourceType(),
                    log.getResourceName() != null ? log.getResourceName() : "N/A",
                    log.getUserEmail() != null ? log.getUserEmail() : "N/A"
                );
            })
            .collect(Collectors.toList());

        return new AuditLogStatistics(
            totalCount,
            byActionType,
            byResourceType,
            byStatus,
            bySeverity,
            recentActivity
        );
    }

    // DTO classes for statistics
    public static class ActionTypeCount {
        private final AuditActionType actionType;
        private final long count;

        public ActionTypeCount(AuditActionType actionType, long count) {
            this.actionType = actionType;
            this.count = count;
        }

        public AuditActionType getActionType() { return actionType; }
        public long getCount() { return count; }
    }

    public static class ResourceTypeCount {
        private final AuditResourceType resourceType;
        private final long count;

        public ResourceTypeCount(AuditResourceType resourceType, long count) {
            this.resourceType = resourceType;
            this.count = count;
        }

        public AuditResourceType getResourceType() { return resourceType; }
        public long getCount() { return count; }
    }

    public static class StatusCount {
        private final AuditStatus status;
        private final long count;

        public StatusCount(AuditStatus status, long count) {
            this.status = status;
            this.count = count;
        }

        public AuditStatus getStatus() { return status; }
        public long getCount() { return count; }
    }

    public static class SeverityCount {
        private final AuditSeverity severity;
        private final long count;

        public SeverityCount(AuditSeverity severity, long count) {
            this.severity = severity;
            this.count = count;
        }

        public AuditSeverity getSeverity() { return severity; }
        public long getCount() { return count; }
    }

    public static class RecentActivity {
        private final long timestamp;
        private final AuditActionType actionType;
        private final AuditResourceType resourceType;
        private final String resourceName;
        private final String userEmail;

        public RecentActivity(long timestamp, AuditActionType actionType, AuditResourceType resourceType, 
                             String resourceName, String userEmail) {
            this.timestamp = timestamp;
            this.actionType = actionType;
            this.resourceType = resourceType;
            this.resourceName = resourceName;
            this.userEmail = userEmail;
        }

        public long getTimestamp() { return timestamp; }
        public AuditActionType getActionType() { return actionType; }
        public AuditResourceType getResourceType() { return resourceType; }
        public String getResourceName() { return resourceName; }
        public String getUserEmail() { return userEmail; }
    }

    public static class AuditLogStatistics {
        private final long totalCount;
        private final List<ActionTypeCount> byActionType;
        private final List<ResourceTypeCount> byResourceType;
        private final List<StatusCount> byStatus;
        private final List<SeverityCount> bySeverity;
        private final List<RecentActivity> recentActivity;

        public AuditLogStatistics(long totalCount, List<ActionTypeCount> byActionType, 
                                 List<ResourceTypeCount> byResourceType, List<StatusCount> byStatus,
                                 List<SeverityCount> bySeverity, List<RecentActivity> recentActivity) {
            this.totalCount = totalCount;
            this.byActionType = byActionType;
            this.byResourceType = byResourceType;
            this.byStatus = byStatus;
            this.bySeverity = bySeverity;
            this.recentActivity = recentActivity;
        }

        public long getTotalCount() { return totalCount; }
        public List<ActionTypeCount> getByActionType() { return byActionType; }
        public List<ResourceTypeCount> getByResourceType() { return byResourceType; }
        public List<StatusCount> getByStatus() { return byStatus; }
        public List<SeverityCount> getBySeverity() { return bySeverity; }
        public List<RecentActivity> getRecentActivity() { return recentActivity; }
    }

    // Input DTOs
    public static class AuditLogFilter {
        private String userId;
        private AuditActionType actionType;
        private AuditResourceType resourceType;
        private String resourceId;
        private String organizationId;
        private String clusterId;
        private AuditStatus status;
        private AuditSeverity severity;
        private Long startTime;
        private Long endTime;
        private String searchText;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public AuditActionType getActionType() { return actionType; }
        public void setActionType(AuditActionType actionType) { this.actionType = actionType; }
        public AuditResourceType getResourceType() { return resourceType; }
        public void setResourceType(AuditResourceType resourceType) { this.resourceType = resourceType; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public String getClusterId() { return clusterId; }
        public void setClusterId(String clusterId) { this.clusterId = clusterId; }
        public AuditStatus getStatus() { return status; }
        public void setStatus(AuditStatus status) { this.status = status; }
        public AuditSeverity getSeverity() { return severity; }
        public void setSeverity(AuditSeverity severity) { this.severity = severity; }
        public Long getStartTime() { return startTime; }
        public void setStartTime(Long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }
    }

    public static class AuditLogPagination {
        private int page = 0;
        private int size = 50;
        private String sortBy = "timestamp";
        private String sortDirection = "DESC";

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    }
}

