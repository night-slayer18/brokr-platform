package io.brokr.api.service;

import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.KsqlDB;
import io.brokr.core.model.KsqlQueryHistory;
import io.brokr.core.model.User;
import io.brokr.kafka.service.KsqlDBService;
import io.brokr.security.service.AuthorizationService;
import io.brokr.storage.entity.KsqlDBEntity;
import io.brokr.storage.entity.KsqlQueryHistoryEntity;
import io.brokr.storage.repository.KsqlDBRepository;
import io.brokr.storage.repository.KsqlQueryHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KsqlDBQueryService {

    private final KsqlDBRepository ksqlDBRepository;
    private final KsqlQueryHistoryRepository queryHistoryRepository;
    private final KsqlDBService ksqlDBService;
    private final AuthorizationService authorizationService;

    /**
     * Execute a ksqlDB query (SELECT statements)
     */
    @Transactional
    public KsqlDBService.KsqlQueryResult executeQuery(String ksqlDBId, String query, Map<String, Object> properties) {
        User currentUser = authorizationService.getCurrentUser();
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        Integer rowsReturned = null;

        try {
            KsqlDBService.KsqlQueryResult result = ksqlDBService.executeQuery(ksqlDB, query, properties);
            long executionTime = System.currentTimeMillis() - startTime;

            rowsReturned = result.getRows() != null ? result.getRows().size() : 0;
            result.setExecutionTimeMs(executionTime);

            // Save to history
            saveQueryHistory(ksqlDBId, currentUser.getId(), query, "SELECT", status, 
                    executionTime, rowsReturned, null, properties);

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            status = "FAILURE";
            errorMessage = e.getMessage();

            // Save to history
            saveQueryHistory(ksqlDBId, currentUser.getId(), query, "SELECT", status, 
                    executionTime, null, errorMessage, properties);

            throw new RuntimeException("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a ksqlDB statement (DDL statements like CREATE, DROP, etc.)
     */
    @Transactional
    public KsqlDBService.KsqlStatementResult executeStatement(String ksqlDBId, String statement, Map<String, Object> properties) {
        User currentUser = authorizationService.getCurrentUser();
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        long startTime = System.currentTimeMillis();
        String queryType = determineQueryType(statement);
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            KsqlDBService.KsqlStatementResult result = ksqlDBService.executeStatement(ksqlDB, statement, properties);
            long executionTime = System.currentTimeMillis() - startTime;

            if (result.getErrorMessage() != null) {
                status = "FAILURE";
                errorMessage = result.getErrorMessage();
            }

            // Save to history
            saveQueryHistory(ksqlDBId, currentUser.getId(), statement, queryType, status, 
                    executionTime, null, errorMessage, properties);

            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            status = "FAILURE";
            errorMessage = e.getMessage();

            // Save to history
            saveQueryHistory(ksqlDBId, currentUser.getId(), statement, queryType, status, 
                    executionTime, null, errorMessage, properties);

            throw new RuntimeException("Statement execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Terminate a running query
     */
    @Transactional
    public boolean terminateQuery(String ksqlDBId, String queryId) {
        KsqlDB ksqlDB = getKsqlDB(ksqlDBId);

        boolean success = ksqlDBService.terminateQuery(ksqlDB, queryId);
        
        if (success) {
            // Update query history status to CANCELLED
            queryHistoryRepository.findByKsqlDbId(ksqlDBId, PageRequest.of(0, 100))
                    .getContent().stream()
                    .filter(h -> queryId.equals(h.getId()) || h.getQueryText().contains(queryId))
                    .findFirst()
                    .ifPresent(history -> {
                        history.setStatus("CANCELLED");
                        history.setCompletedAt(LocalDateTime.now());
                        queryHistoryRepository.save(history);
                    });
        }

        return success;
    }

    /**
     * Get query history with filters
     */
    @Transactional(readOnly = true)
    public Page<KsqlQueryHistory> getQueryHistory(String ksqlDBId, String queryType, String status, 
                                                  Long startDate, Long endDate, int page, int size) {
        getKsqlDB(ksqlDBId); // Verify access

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<KsqlQueryHistoryEntity> entities;
        
        if (queryType != null && status != null) {
            entities = queryHistoryRepository.findByKsqlDbIdAndStatusAndQueryType(ksqlDBId, status, queryType, pageable);
        } else if (queryType != null) {
            entities = queryHistoryRepository.findByKsqlDbIdAndQueryType(ksqlDBId, queryType, pageable);
        } else if (status != null) {
            entities = queryHistoryRepository.findByKsqlDbIdAndStatus(ksqlDBId, status, pageable);
        } else if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(startDate), 
                    java.time.ZoneId.systemDefault());
            LocalDateTime end = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(endDate), 
                    java.time.ZoneId.systemDefault());
            entities = queryHistoryRepository.findByKsqlDbIdAndStartedAtBetween(ksqlDBId, start, end, pageable);
        } else {
            entities = queryHistoryRepository.findByKsqlDbId(ksqlDBId, pageable);
        }

        return entities.map(KsqlQueryHistoryEntity::toDomain);
    }

    /**
     * Get query history by ID
     */
    @Transactional(readOnly = true)
    public KsqlQueryHistory getQueryHistoryById(String id) {
        return queryHistoryRepository.findById(id)
                .map(KsqlQueryHistoryEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Query history not found with id: " + id));
    }

    /**
     * Delete old query history
     */
    @Transactional
    public int deleteQueryHistory(String ksqlDBId, int olderThanDays) {
        getKsqlDB(ksqlDBId); // Verify access

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);
        List<KsqlQueryHistoryEntity> toDelete = queryHistoryRepository.findByKsqlDbIdAndStartedAtBefore(ksqlDBId, cutoffDate);
        
        int count = toDelete.size();
        queryHistoryRepository.deleteAll(toDelete);
        
        return count;
    }

    private KsqlDB getKsqlDB(String id) {
        return ksqlDBRepository.findById(id)
                .map(KsqlDBEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("ksqlDB instance not found with id: " + id));
    }

    private void saveQueryHistory(String ksqlDBId, String userId, String queryText, String queryType, 
                                 String status, Long executionTimeMs, Integer rowsReturned, 
                                 String errorMessage, Map<String, Object> properties) {
        try {
            KsqlQueryHistoryEntity history = KsqlQueryHistoryEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .ksqlDbId(ksqlDBId)
                    .userId(userId)
                    .queryText(queryText)
                    .queryType(queryType)
                    .status(status)
                    .executionTimeMs(executionTimeMs)
                    .rowsReturned(rowsReturned)
                    .errorMessage(errorMessage)
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .properties(properties != null ? properties : new HashMap<>())
                    .build();

            queryHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to save query history: {}", e.getMessage());
        }
    }

    private String determineQueryType(String statement) {
        String upper = statement.trim().toUpperCase();
        if (upper.startsWith("CREATE STREAM")) {
            return "CREATE_STREAM";
        } else if (upper.startsWith("CREATE TABLE")) {
            return "CREATE_TABLE";
        } else if (upper.startsWith("DROP STREAM")) {
            return "DROP_STREAM";
        } else if (upper.startsWith("DROP TABLE")) {
            return "DROP_TABLE";
        } else if (upper.startsWith("TERMINATE")) {
            return "TERMINATE";
        } else if (upper.startsWith("INSERT")) {
            return "INSERT";
        } else if (upper.startsWith("SHOW")) {
            return "SHOW";
        } else if (upper.startsWith("DESCRIBE")) {
            return "DESCRIBE";
        } else if (upper.startsWith("EXPLAIN")) {
            return "EXPLAIN";
        }
        return "OTHER";
    }
}

