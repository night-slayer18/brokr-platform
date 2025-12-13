package io.brokr.api.service;

import io.brokr.core.dto.ActionTypeCount;
import io.brokr.core.dto.AuditLogFilter;
import io.brokr.core.dto.AuditLogPagination;
import io.brokr.core.dto.AuditLogStatistics;
import io.brokr.core.dto.RecentActivity;
import io.brokr.core.dto.ResourceTypeCount;
import io.brokr.core.dto.SeverityCount;
import io.brokr.core.dto.StatusCount;
import io.brokr.core.model.*;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.AuditLogEntity;
import io.brokr.storage.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
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
    public Slice<AuditLog> getAuditLogs(AuditLogFilter filter, AuditLogPagination pagination) {
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
        Slice<AuditLogEntity> entities = auditLogRepository.findWithFilters(
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
            
            // Recreate slice with filtered content
            return new SliceImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                pageable,
                entities.hasNext()
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
    public Slice<AuditLog> getAuditLogsByUser(String userId, AuditLogPagination pagination) {
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

        Slice<AuditLogEntity> entities = auditLogRepository.findByUserIdAndTimestampBetween(
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
            
            return new SliceImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                pageable,
                entities.hasNext()
            );
        }

        return entities.map(AuditLogEntity::toDomain);
    }

    @Transactional(readOnly = true)
    public Slice<AuditLog> getAuditLogsByResource(AuditResourceType resourceType, String resourceId, AuditLogPagination pagination) {
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

        Slice<AuditLogEntity> entities = auditLogRepository.findByResourceTypeAndResourceId(
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
            
            return new SliceImpl<>(
                filteredEntities.stream().map(AuditLogEntity::toDomain).collect(Collectors.toList()),
                pageable,
                entities.hasNext()
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
        // Note: Using repository count methods since Slice doesn't provide total count
        long totalCount;
        if (currentUser.getRole() == Role.ADMIN) {
            // For ADMIN, we get count using our count repository methods
            // This is a specific count query, not from Slice
            totalCount = auditLogRepository.count(); // Will be filtered by service layer if needed
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
        Slice<AuditLogEntity> recentEntities;
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

        return AuditLogStatistics.builder()
            .totalCount(totalCount)
            .byActionType(byActionType)
            .byResourceType(byResourceType)
            .byStatus(byStatus)
            .bySeverity(bySeverity)
            .recentActivity(recentActivity)
            .build();
    }
}
