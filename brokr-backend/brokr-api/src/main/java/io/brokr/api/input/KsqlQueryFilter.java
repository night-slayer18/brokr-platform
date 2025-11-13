package io.brokr.api.input;

import lombok.Data;

@Data
public class KsqlQueryFilter {
    private String queryType;
    private String status;
    private Long startDate;
    private Long endDate;
}

