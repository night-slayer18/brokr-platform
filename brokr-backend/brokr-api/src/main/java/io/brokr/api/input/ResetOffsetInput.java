package io.brokr.api.input;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResetOffsetInput {

    @NotBlank(message = "Field 'topic' is required")
    private String topic;

    @NotNull(message = "Field 'partition' is required")
    @Min(value = 0, message = "Partition must be a non-negative number")
    private int partition;

    @NotNull(message = "Field 'offset' is required")
    @Min(value = 0, message = "Offset must be a non-negative number")
    private long offset;
}