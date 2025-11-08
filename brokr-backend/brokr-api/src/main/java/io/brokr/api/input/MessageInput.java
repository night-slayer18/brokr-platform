package io.brokr.api.input;

import lombok.Data;

import java.util.List;

@Data
public class MessageInput {
    private String topic;
    private List<Integer> partitions;
    private Long offset;
    private Integer limit;
}
