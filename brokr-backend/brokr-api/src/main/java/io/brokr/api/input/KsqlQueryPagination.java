package io.brokr.api.input;

import lombok.Data;

@Data
public class KsqlQueryPagination {
    private int page;
    private int size;
    private String sortBy;
    private String sortDirection;
}

