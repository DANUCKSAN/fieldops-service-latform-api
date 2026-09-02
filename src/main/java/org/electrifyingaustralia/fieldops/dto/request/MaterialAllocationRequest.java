package org.electrifyingaustralia.fieldops.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MaterialAllocationRequest(
        @NotNull UUID productId,
        @Positive @Max(1_000_000) int quantity
) {
}
