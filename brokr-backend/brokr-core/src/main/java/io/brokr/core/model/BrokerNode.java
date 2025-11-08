package io.brokr.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrokerNode {
    private int id;
    private String host;
    private int port;
    private String rack;
}
