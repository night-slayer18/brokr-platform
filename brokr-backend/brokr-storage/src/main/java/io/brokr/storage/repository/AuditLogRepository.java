package io.brokr.storage.repository;

import io.brokr.core.model.AuditActionType;
import io.brokr.core.model.AuditResourceType;
import io.brokr.core.model.AuditSeverity;
import io.brokr.core.model.AuditStatus;
import io.brokr.storage.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    
    // Find by user and time range
    Page<AuditLogEntity> findByUserIdAndTimestampBetween(
            String userId, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by resource
    Page<AuditLogEntity> findByResourceTypeAndResourceId(
            AuditResourceType resourceType, 
            String resourceId, 
            Pageable pageable
    );
    
    // Find by resource and time range
    Page<AuditLogEntity> findByResourceTypeAndResourceIdAndTimestampBetween(
            AuditResourceType resourceType, 
            String resourceId, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by organization and time range
    Page<AuditLogEntity> findByOrganizationIdAndTimestampBetween(
            String organizationId, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by cluster and time range
    Page<AuditLogEntity> findByClusterIdAndTimestampBetween(
            String clusterId, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by action type and time range
    Page<AuditLogEntity> findByActionTypeAndTimestampBetween(
            AuditActionType actionType, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by status and time range
    Page<AuditLogEntity> findByStatusAndTimestampBetween(
            AuditStatus status, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by severity and time range
    Page<AuditLogEntity> findBySeverityAndTimestampBetween(
            AuditSeverity severity, 
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Find by time range
    Page<AuditLogEntity> findByTimestampBetween(
            LocalDateTime startTime, 
            LocalDateTime endTime, 
            Pageable pageable
    );
    
    // Delete old logs (for retention)
    void deleteByTimestampBefore(LocalDateTime timestamp);
    
    // Count by action type in time range
    long countByActionTypeAndTimestampBetween(
            AuditActionType actionType, 
            LocalDateTime startTime, 
            LocalDateTime endTime
    );
    
    // Count by resource type in time range
    long countByResourceTypeAndTimestampBetween(
            AuditResourceType resourceType, 
            LocalDateTime startTime, 
            LocalDateTime endTime
    );
    
    // Count by status in time range
    long countByStatusAndTimestampBetween(
            AuditStatus status, 
            LocalDateTime startTime, 
            LocalDateTime endTime
    );
    
    // Count by severity in time range
    long countBySeverityAndTimestampBetween(
            AuditSeverity severity, 
            LocalDateTime startTime, 
            LocalDateTime endTime
    );
    
    // Complex query with multiple filters
    @Query(value = "SELECT * FROM audit_logs a WHERE " +
           "(:userId IS NULL OR a.user_id = :userId) AND " +
           "(:actionType IS NULL OR a.action_type = :actionType) AND " +
           "(:resourceType IS NULL OR a.resource_type = :resourceType) AND " +
           "(:resourceId IS NULL OR a.resource_id = :resourceId) AND " +
           "(:organizationId IS NULL OR a.organization_id = :organizationId) AND " +
           "(:clusterId IS NULL OR a.cluster_id = :clusterId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:severity IS NULL OR a.severity = :severity) AND " +
           "(:hasStartTime = false OR a.timestamp >= :startTime) AND " +
           "(:hasEndTime = false OR a.timestamp <= :endTime) AND " +
           "(:searchText IS NULL OR " +
           "  (a.resource_name IS NOT NULL AND LOWER(a.resource_name) LIKE LOWER('%' || :searchText || '%')) OR " +
           "  (a.user_email IS NOT NULL AND LOWER(a.user_email) LIKE LOWER('%' || :searchText || '%')) OR " +
           "  (a.user_id IS NOT NULL AND LOWER(a.user_id) LIKE LOWER('%' || :searchText || '%')))",
           nativeQuery = true)
    Page<AuditLogEntity> findWithFilters(
            @Param("userId") String userId,
            @Param("actionType") String actionType,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("organizationId") String organizationId,
            @Param("clusterId") String clusterId,
            @Param("status") String status,
            @Param("severity") String severity,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("hasStartTime") boolean hasStartTime,
            @Param("hasEndTime") boolean hasEndTime,
            @Param("searchText") String searchText,
            Pageable pageable
    );
}

