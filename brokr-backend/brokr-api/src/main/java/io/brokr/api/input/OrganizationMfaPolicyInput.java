package io.brokr.api.input;

import lombok.Data;

@Data
public class OrganizationMfaPolicyInput {
    private Boolean mfaRequired;
    private Integer mfaGracePeriodDays;
}


