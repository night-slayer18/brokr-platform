package io.brokr.api.service;

import io.brokr.core.model.KsqlQueryMetrics;
import io.brokr.storage.entity.KsqlQueryMetricsEntity;
import io.brokr.storage.repository.KsqlQueryMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KsqlDBMetricsService {

    private final KsqlQueryMetricsRepository metricsRepository;

    @Transactional(readOnly = true)
    public List<KsqlQueryMetrics> getQueryMetrics(String queryHistoryId) {
        return metricsRepository.findByQueryHistoryId(queryHistoryId, 
                org.springframework.data.domain.PageRequest.of(0, 1000))
                .getContent().stream()
                .map(KsqlQueryMetricsEntity::toDomain)
                .collect(Collectors.toList());
    }
}

