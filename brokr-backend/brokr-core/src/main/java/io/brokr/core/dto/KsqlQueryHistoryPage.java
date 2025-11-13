package io.brokr.core.dto;

import io.brokr.core.model.KsqlQueryHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsqlQueryHistoryPage {
    private List<KsqlQueryHistory> content;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}

