package io.brokr.api.graphql;

import io.brokr.api.input.KsqlQueryFilter;
import io.brokr.api.input.KsqlQueryInput;
import io.brokr.api.input.KsqlQueryPagination;
import io.brokr.api.service.KsqlDBApiService;
import io.brokr.api.service.KsqlDBMetricsService;
import io.brokr.api.service.KsqlDBQueryService;
import io.brokr.api.service.KsqlDBStreamTableService;
import io.brokr.api.service.UserApiService;
import io.brokr.core.dto.KsqlQueryHistoryPage;
import io.brokr.core.dto.UserDto;
import io.brokr.core.model.KsqlDB;
import io.brokr.core.model.KsqlQueryHistory;
import io.brokr.core.model.KsqlQueryMetrics;
import io.brokr.core.model.KsqlStreamTable;
import io.brokr.kafka.service.KsqlDBService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.ZoneId;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class KsqlDBQueryResolver {

    private final KsqlDBQueryService queryService;
    private final KsqlDBStreamTableService streamTableService;
    private final KsqlDBApiService ksqlDBApiService;
    private final UserApiService userApiService;
    private final KsqlDBMetricsService metricsService;

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlDBService.KsqlQueryResult executeKsqlQuery(
            @Argument String ksqlDBId,
            @Argument KsqlQueryInput input) {
        return queryService.executeQuery(ksqlDBId, input.getQuery(), input.getProperties());
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public List<KsqlStreamTable> ksqlStreams(@Argument String ksqlDBId) {
        return streamTableService.getStreams(ksqlDBId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public List<KsqlStreamTable> ksqlTables(@Argument String ksqlDBId) {
        return streamTableService.getTables(ksqlDBId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlStreamTable ksqlStreamTable(
            @Argument String ksqlDBId,
            @Argument String name) {
        return streamTableService.getStreamTable(ksqlDBId, name);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlQueryHistoryPage ksqlQueryHistory(
            @Argument String ksqlDBId,
            @Argument KsqlQueryFilter filter,
            @Argument KsqlQueryPagination pagination) {
        
        String queryType = filter != null && filter.getQueryType() != null ? filter.getQueryType() : null;
        String status = filter != null && filter.getStatus() != null ? filter.getStatus() : null;
        Long startDate = filter != null ? filter.getStartDate() : null;
        Long endDate = filter != null ? filter.getEndDate() : null;
        
        int page = pagination != null ? pagination.getPage() : 0;
        int size = pagination != null ? pagination.getSize() : 50;
        
        Page<KsqlQueryHistory> historyPage = queryService.getQueryHistory(
                ksqlDBId, queryType, status, startDate, endDate, page, size);
        
        return KsqlQueryHistoryPage.builder()
                .content(historyPage.getContent())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .currentPage(historyPage.getNumber())
                .pageSize(historyPage.getSize())
                .build();
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#id)")
    public KsqlQueryHistory ksqlQueryHistoryById(@Argument String id) {
        return queryService.getQueryHistoryById(id);
    }

    @QueryMapping
    public List<KsqlQueryMetrics> ksqlQueryMetrics(@Argument String queryHistoryId) {
        return metricsService.getQueryMetrics(queryHistoryId);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlDBService.KsqlQueryResult executeKsqlStatement(
            @Argument String ksqlDBId,
            @Argument KsqlQueryInput input) {
        KsqlDBService.KsqlStatementResult result = queryService.executeStatement(ksqlDBId, input.getQuery(), input.getProperties());
        
        // Convert StatementResult to QueryResult for GraphQL
        KsqlDBService.KsqlQueryResult queryResult = new KsqlDBService.KsqlQueryResult();
        queryResult.setQueryId(result.getCommandId());
        queryResult.setColumns(List.of("status", "message"));
        queryResult.setRows(List.of(List.of(
                result.getCommandStatus() != null ? result.getCommandStatus() : "UNKNOWN",
                result.getMessage() != null ? result.getMessage() : ""
        )));
        if (result.getErrorMessage() != null) {
            queryResult.setErrorMessage(result.getErrorMessage());
        }
        return queryResult;
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public boolean terminateKsqlQuery(
            @Argument String ksqlDBId,
            @Argument String queryId) {
        return queryService.terminateQuery(ksqlDBId, queryId);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlStreamTable createKsqlStream(
            @Argument String ksqlDBId,
            @Argument KsqlQueryInput input) {
        return streamTableService.createStream(ksqlDBId, input.getQuery());
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public KsqlStreamTable createKsqlTable(
            @Argument String ksqlDBId,
            @Argument KsqlQueryInput input) {
        return streamTableService.createTable(ksqlDBId, input.getQuery());
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public boolean dropKsqlStream(
            @Argument String ksqlDBId,
            @Argument String streamName) {
        return streamTableService.dropStream(ksqlDBId, streamName);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public boolean dropKsqlTable(
            @Argument String ksqlDBId,
            @Argument String tableName) {
        return streamTableService.dropTable(ksqlDBId, tableName);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToKsqlDB(#ksqlDBId)")
    public int deleteKsqlQueryHistory(
            @Argument String ksqlDBId,
            @Argument int olderThanDays) {
        return queryService.deleteQueryHistory(ksqlDBId, olderThanDays);
    }

    // Schema mappings for nested fields
    @SchemaMapping(typeName = "KsqlDB", field = "streams")
    public List<KsqlStreamTable> streams(KsqlDB ksqlDB) {
        return streamTableService.getStreams(ksqlDB.getId());
    }

    @SchemaMapping(typeName = "KsqlDB", field = "tables")
    public List<KsqlStreamTable> tables(KsqlDB ksqlDB) {
        return streamTableService.getTables(ksqlDB.getId());
    }

    @SchemaMapping(typeName = "KsqlQueryHistory", field = "startedAt")
    public Long startedAt(KsqlQueryHistory history) {
        if (history.getStartedAt() == null) {
            return null;
        }
        return history.getStartedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @SchemaMapping(typeName = "KsqlQueryHistory", field = "completedAt")
    public Long completedAt(KsqlQueryHistory history) {
        if (history.getCompletedAt() == null) {
            return null;
        }
        return history.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @SchemaMapping(typeName = "KsqlStreamTable", field = "createdAt")
    public Long createdAt(KsqlStreamTable streamTable) {
        if (streamTable.getCreatedAt() == null) {
            return null;
        }
        return streamTable.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @SchemaMapping(typeName = "KsqlStreamTable", field = "updatedAt")
    public Long updatedAt(KsqlStreamTable streamTable) {
        if (streamTable.getUpdatedAt() == null) {
            return null;
        }
        return streamTable.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @SchemaMapping(typeName = "KsqlQueryMetrics", field = "timestamp")
    public Long timestamp(KsqlQueryMetrics metrics) {
        if (metrics.getTimestamp() == null) {
            return null;
        }
        return metrics.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @SchemaMapping(typeName = "KsqlQueryHistory", field = "ksqlDB")
    public KsqlDB ksqlDB(KsqlQueryHistory history) {
        return ksqlDBApiService.getKsqlDBById(history.getKsqlDbId());
    }

    @SchemaMapping(typeName = "KsqlQueryHistory", field = "user")
    public UserDto user(KsqlQueryHistory history) {
        return UserDto.fromDomain(userApiService.getUserById(history.getUserId()));
    }

    @SchemaMapping(typeName = "KsqlStreamTable", field = "ksqlDB")
    public KsqlDB ksqlDB(KsqlStreamTable streamTable) {
        return ksqlDBApiService.getKsqlDBById(streamTable.getKsqlDbId());
    }
}

